# ScalaX2Gether Workshop

The code for *Type-Oriented Programming: Building and using libraries for self-assembling programs* at
ScalaX Community Day 2018.

We're starting with this skeleton, and we're going to commit solutions to new branches as we go. I'll update
the master branch with a listing of which branches to look at after the fact.

The slides are available at https://jeremyrsmith.github.io/scalax-workshop

*Update*: Branches to look at:

| Branch              | Description                                                                        |
| ------------------- | ---------------------------------------------------------------------------------- |
| `ord-done`          | After we implemented `Ord` derivations for case classes and hierarchies            |
| `trees-skeleton`    | Before implementing any tree stuff (includes test harness and some auxiliary code) |
| `trees-find-split`  | After implementing the `FindSplit` typeclass                                       |
| `trees-find-splits` | After implementing the `FindSplits` typeclass                                      |
| `trees-working`     | Finished trees, after implementing `FindSplits.WithLenses`                         |
| `gh-pages`          | The slides from the workshop                                                       |


Since we didn't have time to get completely into the trees, I'll go through and add explanatory comments on that code for a future update.

As always, happy to answer any questions about shapeless (or the code here). The shapeless gitter channel (`milessabin/shapeless`) seems like an appropriate forum, but feel free to DM me on gitter as well.
