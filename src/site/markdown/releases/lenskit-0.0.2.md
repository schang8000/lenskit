Title: Release 0.0.2


# Release 0.0.2 (March 28, 2011)

This release contains a number of API changes to make the API
cleaner and easier to move forward.

-   Renamed `BuildContext` to `RatingBuildContext` to allow
    for a future `BasketBuildContext`.

-   Added support for excluded items to recommendation API.

-   Added `AbstractRatingRecommender` helper class to make
    implementing rating recommenders easier.

-   Removed the `RecommenderService` class and several related
    classes to clean up the API.  Client code should directly request
    `RatingRecommender` or `RatingPredictor` instances, and
    modules should scope them and their dependencies appropriately to
    avoid duplicating recommender models.

## API Compatibility Notes

-   Code dependent on `BuildContext` or `PackedBuildContext`
    must be updated to use `RatingBuildContext` and
    `PackedRatingBuildContext` instead.

-   Code implementing `RatingRecommender` must be updated to
    implement the new API.  We recommend using
    `AbstractRatingRecommender` to help with this.

-   All code must be updated to directly receive injected
    `RatingRecommender` and/or `RatingPredictor` instances
    (or providers thereof).
