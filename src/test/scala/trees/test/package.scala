package trees

import shapeless.Generic

import scala.util.Random

package object test {

  // let's say we've been in business around 6 years.
  final val DaysInBusiness = 365 * 6

  final case class Features(
    price: Double,
    daysAvailable: Int,
    previouslyBought: Boolean,
    isConsumable: Boolean,
    previouslyBoughtLinkedProduct: Boolean,
    daysSinceLastPurchase: Int,
    averageDaysBetweenPurchases: Int,
    userMonthlyAveragePurchaseTotal: Double,
    avgProductRating: Double,
    userProductRating: Double
  )

  private def gaussian(mean: Double, stdDev: Double = 1.0): Double = Random.nextGaussian() * stdDev + mean
  private def limit[T](lower: T, upper: T)(t: T)(implicit ord: Ordering[T]): T =
    ord.min(upper, ord.max(lower, t))

  import Math.{min, max}
  import Random.{nextInt, nextDouble}

  // attempts to generate features according to a "realistic" decision process.
  def generateData: Stream[(Features, Double)] = {
    // first let's make up some numbers about the user, the product, and their relationship

    // let's say ~20% of products are "consumables"
    val consumable = nextDouble() < 0.2

    // the product has been available for a random number of days, but is less likely as we approach 6 years (due to
    // attrition) and less likely as we approach 0 (the number of products added per day is small compared to the number
    // of total products). Let's say it's roughly a gaussian distribution since that's easy to do.
    val daysAvailable = limit(1, DaysInBusiness)(gaussian(DaysInBusiness * 0.5, DaysInBusiness * 64).toInt)

    // Let's say people spend $100/month on average
    val myAverageMonthlySpend = max(0.0, gaussian(100.0, 100.0))


    // product ratings range from 1 - 5, and most products are somewhere near the middle.
    // there's a chance this is a particularly good or bad product
    val productGoodness = limit(1.0, 5.0)(Random.nextGaussian() * 2.0 + 2.0)

    // and the average rating is generally consistent with the product goodness
    val avgProductRating = limit(1.0, 5.0)(gaussian(productGoodness))

    // if I bought this product and its goodness is low, there's a decent chance I hated it.
    val hatedProduct = gaussian(productGoodness) < 2

    val current = if (consumable) {

      // if it's a consumable product, let's say it lasts 30-120 days.
      val lifetime = Random.nextInt(90) + 30

      // if the lifetime is longer than it's been available, then there's an 80% chance nobody's bought it twice yet.
      val nobodyBoughtTwice = lifetime > daysAvailable && nextDouble() > 0.2

      // Otherwise time between purchase is a normal distribution(ish)
      val averageDaysBetweenPurchases = if (nobodyBoughtTwice)
        0
      else
        min(daysAvailable - 1, gaussian(daysAvailable, daysAvailable / 2).toInt)

      // There's about a 30% chance we've bought it before, in which case it was a random number of days ago,
      // but unlikely to be much more than the average or we would have bought it again – unless we're not planning to
      // buy it again, in which case there's a 20% chance we left a negative review.
      val (daysSinceLastPurchase, myProductRating) = if (Random.nextDouble() > 0.3) (0, Double.NaN) else {
        if (hatedProduct) {
          (nextInt(daysAvailable), if (nextDouble() < 0.2) max(1.0, gaussian(1)) else Double.NaN)
        } else {
          // there's a 5% chance we left a product rating, which is close to the average
          val myRating = if (nextDouble() < .05) limit(1.0, 5.0)(gaussian(avgProductRating)) else Double.NaN

          (limit(0, daysAvailable)(Random.nextInt(lifetime) + gaussian(lifetime / 4, lifetime / 4).toInt), myRating)
        }
      }

      // let's say consumables cost $20 on average, but no less than $5.
      val price = max(5.0, gaussian(20.0, 15.0))

      // let's say there's a 10% chance that I've previously bought the thing that consumes this
      val previouslyBoughtLinkedProduct = nextDouble() < 0.1

      val features = Features(
        price = price,
        daysAvailable = daysAvailable,
        previouslyBought = daysSinceLastPurchase > 0,
        isConsumable = true,
        previouslyBoughtLinkedProduct = previouslyBoughtLinkedProduct,
        daysSinceLastPurchase = daysSinceLastPurchase,
        averageDaysBetweenPurchases = averageDaysBetweenPurchases,
        userMonthlyAveragePurchaseTotal = myAverageMonthlySpend,
        avgProductRating = avgProductRating,
        userProductRating = myProductRating
      )

      // now let's decide whether I buy this consumable.
      val label = if (daysSinceLastPurchase > 0) { // if I bought it before
        if (hatedProduct)
          0.0 // never buying THAT again!
        else if (gaussian(lifetime, lifetime / 8) - daysSinceLastPurchase < 1)  // chances I need to reorder now
          1.0
        else
          0.0
      } else {
        // if I haven't bought it before, then there's a chance I need this thing - especially if I bought the linked product
        val needIt = (previouslyBoughtLinkedProduct && nextDouble() < 0.9) ||
          nextDouble() < 0.05 // let's say there's a 5% chance I bought the linked product elsewhere

        // if I need it, there's a pretty good chance I'm going to buy it - let's say on average I will, but sometimes
        // I won't, like if it's expensive and I've already used up my monthly budget
        val tooExpensive = price > (myAverageMonthlySpend * nextDouble)

        if (needIt) {
          if (tooExpensive) 0.0 else 1.0
        } else if (gaussian(0.0) > 1.0) { // there's a tiny chance I might buy it anyway
          1.0
        } else 0.0
      }

      (features, label)
    } else {
      // not a consumable, so the story is a bit different

      // let's say there's a 10% chance this is a "big purchase", like an iPad or something, which has an average price of $300.
      // Otherwise it's a "normal purchase", average $50. Nothing costs under $3.00.
      val price = max(3.0, if (nextDouble() < 0.1) gaussian(300.0, 100.0) else gaussian(50.0, 50.0))

      // there's a small chance I own something related to this
      val ownRelated = nextDouble() < 0.1

      // if I own the related thing, there's a 50% chance you know about it
      val previouslyBoughtLinkedProduct = ownRelated && nextDouble() < 0.5

      // how much do I want something like this? There's a 60% chance it's something I don't care about at all, unless I own something related and then I'm 70% likely to want it
      val wantIt = if (ownRelated) nextDouble() < 0.7 else nextDouble() < 0.4

      // if I want it, there's a 20% chance I already bought it from here, plus another 20% chance if I own the related thing
      val alreadyBoughtFromHere = nextDouble() < 0.2 || (ownRelated && nextDouble() < 0.2)

      // if I already bought it from you, that was a certain number of days ago... likely to be closer to when it was released
      val daysSincePurchased = if (alreadyBoughtFromHere) limit(1, daysAvailable - 1)(gaussian(daysAvailable - 4, daysAvailable / 8).toInt) else 0

      // there's also a 10% chance I bought it from somewhere else and you don't know about it
      val ownIt = alreadyBoughtFromHere || (wantIt && nextDouble() < 0.1) || nextDouble() < 0.05

      // let's say I want something like this – if the average reviews are pretty bad, it's going to make me tend to steer clear
      val consideringIt = !ownIt && wantIt && (avgProductRating > 2.2 || nextDouble() > 0.1)

      // if I'm considering it, I'll be more likely to buy it if it's a good product and has good reviews
      val desireToBuy = if (consideringIt)
        max(0, gaussian(productGoodness * avgProductRating / 25, 0.1))
      else
        max(0, gaussian(0.0, 0.05))

      // scale that by how affordable it is, and that's how likely I am to buy it. Let's say, on a scale from 1 to 10
      val likelihood = desireToBuy * (myAverageMonthlySpend / price)

      // There's a 5% chance I left a rating if I own it, and it's more likely to be positive if I also own another related product
      val myProductRating = if (ownIt && nextDouble() < 0.05) limit(1.0, 5.0)(gaussian(if (ownRelated) 4.0 else 2.0, 2.0)) else Double.NaN

      // Now I'll make my final decision with a bit of randomness
      val label = if (gaussian(10.0) < likelihood) 1.0 else 0.0

      val features = Features(
        price = price,
        daysAvailable = daysAvailable,
        previouslyBought = alreadyBoughtFromHere,
        isConsumable = false,
        previouslyBoughtLinkedProduct = previouslyBoughtLinkedProduct,
        daysSinceLastPurchase = daysSincePurchased,
        averageDaysBetweenPurchases = 0,
        userMonthlyAveragePurchaseTotal = myAverageMonthlySpend,
        avgProductRating = avgProductRating,
        userProductRating = myProductRating)

      (features, label)
    }

    // Phew, we created a labeled feature vector. Now do it recursively forever!
    current #:: generateData
  }

}
