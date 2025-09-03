package com.gu.etagcaching.testkit

import java.util.concurrent.atomic.AtomicLong

class CountingParser[K,V](parser: K => V) extends (K => V) {

  private val counter = new AtomicLong()

  override def apply(key: K): V = {
    counter.incrementAndGet()
    parser(key)
  }

  def count(): Long = counter.get()
}
