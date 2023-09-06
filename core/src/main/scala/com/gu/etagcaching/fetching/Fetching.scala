package com.gu.etagcaching.fetching

import com.gu.etagcaching.fetching.Fetching.DurationRecorder.Result
import com.gu.etagcaching.fetching.Fetching._
import com.gu.etagcaching.{Endo, Loading}

import java.time.{Duration, Instant}
import scala.concurrent.{CancellationException, ExecutionContext, Future}
import scala.util.Try

trait Fetching[K, Response, X] {
  def fetch(key: K)(implicit ec: ExecutionContext): Future[Either[X, ETaggedData[Response]]]

  def fetchOnlyIfETagChanged(key: K, eTag: String)(implicit ec: ExecutionContext): Future[Either[X, ETaggedData[Response]]]

  def timing(
    attemptWith: Duration => Unit = _ => (),
    successWith: Duration => Unit = _ => (),
    fullFetchWith: Duration => Unit = _ => (),
    notModifiedWith: Duration => Unit = _ => ()
  ): Fetching[K, Response, X] =
    DurationRecorder(this) { recorded =>
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

  def keyOn[K2](f: K2 => K): Fetching[K2, Response, X] = KeyAdapter(this)(f)

  def mapResponse[Response2](f: Response => Response2): Fetching[K, Response2, X] =
    ResponseTransform(this)(successTransform = f)

//  def suppressExceptionLoggingIf(p: Throwable => Boolean): Fetching[K, Response] =
//    ResponseTransform(this)(identity, failureTransform = { throwable =>
//      if (p(throwable)) // this is admittedly a pretty horrible hack
//        new CancellationException(s"Suppressing Caffeine cache exception logging for '$throwable'")
//      else throwable // Caffeine will log this exception: https://github.com/ben-manes/caffeine/issues/597
//    })

  def thenParsing[V](parse: Response => V): Loading[K, V, X] = Loading.by(this)(parse)
}

object Fetching {
  sealed trait SuccessfulFetch
  case object NotModified extends SuccessfulFetch
  case object FullFetch extends SuccessfulFetch


  object DurationRecorder {
    case class Result(duration: Duration, result: Try[SuccessfulFetch])
  }
  case class DurationRecorder[K, Response, X](underlying: Fetching[K, Response, X])(recorder: Result => Unit)
    extends Fetching[K, Response, X] {

    private def time[V](block: => Future[V])(f: V => SuccessfulFetch)(implicit ec: ExecutionContext): Future[V] = {
      val start = Instant.now()
      val resultF = block
      resultF.onComplete(resultTry => recorder(Result(Duration.between(start, Instant.now()), resultTry.map(f))))
      resultF
    }

    override def fetch(key: K)(implicit ec: ExecutionContext): Future[Either[X, ETaggedData[Response]]] =
      time(underlying.fetch(key))(_ => FullFetch)

    override def fetchOnlyIfETagChanged(key: K, eTag: String)(implicit ec: ExecutionContext): Future[Either[X, ETaggedData[Response]]] =
      time(underlying.fetchOnlyIfETagChanged(key, eTag))(_.map(_ => FullFetch).getOrElse(NotModified))
  }

  private case class KeyAdapter[K, UnderlyingK, Response, X](underlying: Fetching[UnderlyingK, Response, X])(f: K => UnderlyingK)
    extends Fetching[K, Response] {
    override def fetch(key: K)(implicit ec: ExecutionContext): Future[ETaggedData[Response]] =
      underlying.fetch(f(key))

    override def fetchOnlyIfETagChanged(key: K, eTag: String)(implicit ec: ExecutionContext): Future[Option[ETaggedData[Response]]] =
      underlying.fetchOnlyIfETagChanged(f(key), eTag)
  }

  private case class ResponseTransform[K, UnderlyingResponse, Response](underlying: Fetching[K, UnderlyingResponse])(
    successTransform: UnderlyingResponse => Response,
    failureTransform: Endo[Throwable] = identity
  ) extends Fetching[K, Response] {
    override def fetch(key: K)(implicit ec: ExecutionContext): Future[ETaggedData[Response]] =
      underlying.fetch(key).transform(_.map(successTransform), failureTransform)

    override def fetchOnlyIfETagChanged(key: K, eTag: String)(implicit ec: ExecutionContext): Future[Option[ETaggedData[Response]]] =
      underlying.fetchOnlyIfETagChanged(key, eTag).transform(_.map(_.map(successTransform)), failureTransform)
  }
}