package com.gu

import com.github.blemale.scaffeine.Scaffeine

package object etagcaching {

  /**
   * This is a useful type for configuration modifiers (adding more configuration to an existing object).
   *
   * https://en.wikipedia.org/wiki/Endomorphism
   *
   * `cats.Endo` was added to Cats with https://github.com/typelevel/cats/pull/2076 - rather than add a
   * dependency on Cats to this library, re-defining it here.
   */
  type Endo[A] = A => A

  type ConfigCache = Endo[Scaffeine[Any, Any]]
}
