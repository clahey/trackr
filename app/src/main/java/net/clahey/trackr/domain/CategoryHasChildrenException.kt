package net.clahey.trackr.domain

/**
 * Thrown by the in-transaction childlessness guard (DM-DATA-028) when a category is about to
 * be nested as a SubCategory while it still has SubCategory children of its own — which would
 * exceed the two-level hierarchy cap.
 *
 * A dedicated type (rather than a bare `require` / plain [IllegalArgumentException]) lets
 * callers such as `CategoryListViewModel.persistReparent` catch *this* rejection precisely,
 * without also swallowing unrelated argument errors, when turning a rejected reparent into a
 * user-facing snackbar (CAT-UI-084). It extends [IllegalArgumentException] so existing
 * DM-DATA-028 guard semantics are preserved.
 */
class CategoryHasChildrenException(val categoryId: String, val childCount: Int) :
    IllegalArgumentException("Cannot nest category '$categoryId': it has $childCount SubCategory children")
