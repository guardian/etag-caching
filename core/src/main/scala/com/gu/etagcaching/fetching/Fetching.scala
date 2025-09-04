package com.gu.etagcaching.fetching

import com.gu.etagcaching.Loading
import com.gu.etagcaching.fetching.Fetching.DurationRecorder.Result
import com.gu.etagcaching.fetching.Fetching._

import java.time.{Duration, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait Fetching[K, Response] {
  def fetch(key: K): Future[MissingOrETagged[Response]]

  def fetchOnlyIfETagChanged(key: K, eTag: String): Future[Option[MissingOrETagged[Response]]]

  /**
   * @param recordingEC the ExecutionContext used for
   */
  def timing(
    attemptWith: Duration => Unit = _ => (),
    successWith: Duration => Unit = _ => (),
    fullFetchWith: Duration => Unit = _ => (),
    notModifiedWith: Duration => Unit = _ => ()
  )(implicit recordingEC: ExecutionContext): Fetching[K, Response] = DurationRecorder(this) { recorded =>
    val duration = recorded.duration
    attemptWith(duration)
    recorded.result.foreach { successfulResponse =>
      successWith(duration)
      successfulResponse match {
        case FullFetch => fullFetchWith(duration)
        case NotModified => notModifiedWith(duration)
      }
    }
  }

  def keyOn[K2](f: K2 => K): Fetching[K2, Response] = KeyAdapter(this)(f)

  def mapResponse[Response2](f: Response => Response2)(implicit ec: ExecutionContext): Fetching[K, Response2] = ResponseMapper(this)(f)

  /**
   * Transforms this [[Fetching]] instance into a full [[Loading]] instance by saying how to parse
   * the fetched response.
   *
   * If you happen to need the `key` to parse the response, use [[thenParsingWithKey]].
   */
  def thenParsing[V](parse: Response => V)(implicit parsingEC: ExecutionContext): Loading[K, V] = thenParsingWithKey((_, response) => parse(response))

  /**
   * Transforms this [[Fetching]] instance into a full [[Loading]] instance by saying how to parse
   * the fetched response - in this particular case, the parsing method is passed the `key`
   * as well as the fetched response.
   *
   * If you don't need the `key` to parse the response, just use [[thenParsing]].
   */
  def thenParsingWithKey[V](parse: (K, Response) => V)(implicit parsingEC: ExecutionContext): Loading[K, V] = new Loading[K, V] {
    def fetchAndParse(key: K): Future[MissingOrETagged[V]] =
      fetch(key).map(_.map(parse(key, _)))

    def fetchThenParseIfNecessary(key: K, oldV: ETaggedData[V]): Future[MissingOrETagged[V]] =
      fetchOnlyIfETagChanged(key, oldV.eTag).map {
        case None => oldV // we got HTTP 304 'NOT MODIFIED': there's no new data - old data is still valid
        case Some(freshResponse) => freshResponse.map(parse(key, _))
      }
  }
}

object Fetching {
  sealed trait SuccessfulFetch
  case object NotModified extends SuccessfulFetch
  case object FullFetch extends SuccessfulFetch


  object DurationRecorder {
    case class Result(duration: Duration, result: Try[SuccessfulFetch])
  }

  /**
   * @param ec should recording a result happen on the same thread that
   */
  case class DurationRecorder[K, Response](underlying: Fetching[K, Response])(recorder: Result => Unit)(implicit ec: ExecutionContext)
    extends Fetching[K, Response] {

    private def time[V](block: => Future[V])(f: V => SuccessfulFetch): Future[V] = {
      val start = Instant.now()
      val resultF = block
      resultF.onComplete(resultTry => recorder(Result(Duration.between(start, Instant.now()), resultTry.map(f))))
      resultF
    }

    override def fetch(key: K): Future[MissingOrETagged[Response]] =
      time(underlying.fetch(key))(_ => FullFetch)

    override def fetchOnlyIfETagChanged(key: K, eTag: String): Future[Option[MissingOrETagged[Response]]] =
      time(underlying.fetchOnlyIfETagChanged(key, eTag))(_.map(_ => FullFetch).getOrElse(NotModified))
  }

  private case class KeyAdapter[K, UnderlyingK, Response](underlying: Fetching[UnderlyingK, Response])(f: K => UnderlyingK)
    extends Fetching[K, Response] {
    override def fetch(key: K): Future[MissingOrETagged[Response]] =
      underlying.fetch(f(key))

    override def fetchOnlyIfETagChanged(key: K, eTag: String): Future[Option[MissingOrETagged[Response]]] =
      underlying.fetchOnlyIfETagChanged(f(key), eTag)
  }

  private case class ResponseMapper[K, UnderlyingResponse, Response](underlying: Fetching[K, UnderlyingResponse])(
    f: UnderlyingResponse => Response
  )(implicit responseMappingEC: ExecutionContext) // the function `f` _may_ be resource intensive, and require an appropriate ExecutionContext
    extends Fetching[K, Response] {
    override def fetch(key: K): Future[MissingOrETagged[Response]] =
      underlying.fetch(key).map(_.map(f))

    override def fetchOnlyIfETagChanged(key: K, eTag: String): Future[Option[MissingOrETagged[Response]]] =
      underlying.fetchOnlyIfETagChanged(key, eTag).map(_.map(_.map(f)))
  }
}