package com.trackr.app.ui.category

// NOTE: Tests in this file are disabled until Phase 6 Step 3, which adds
// emojiState, colorState, valueTypeState (nullable), effectiveEmoji/Color/ValueType,
// and removeFromGroup() to CategoryEditViewModel.
// Restore from git history when implementing Step 3.

import org.junit.Ignore
import org.junit.Test

@Suppress("TestFunctionName")
class CategoryEditViewModelHierarchyTest {

    // @spec CAT-UI-030
    @Ignore("Enable in Phase 6 Step 3") @Test fun `warning for MetaCategory counts events in inheriting SubCategories`() = Unit

    // @spec CAT-UI-030
    @Ignore("Enable in Phase 6 Step 3") @Test fun `warning for MetaCategory excludes events in SubCategories with explicit valueType`() = Unit

    // @spec CAT-UI-030
    @Ignore("Enable in Phase 6 Step 3") @Test fun `warning for SubCategory uses only its own events`() = Unit

    // @spec CAT-UI-030, CAT-UI-031
    @Ignore("Enable in Phase 6 Step 3") @Test fun `originalValueType for inheriting SubCategory is parent resolved type`() = Unit

    // @spec CAT-UI-043
    @Ignore("Enable in Phase 6 Step 3") @Test fun `SubCategory create mode does not advance color counter`() = Unit

    // @spec CAT-UI-054
    @Ignore("Enable in Phase 6 Step 3") @Test fun `SubCategory create mode opens with null inheritable fields`() = Unit

    // @spec CAT-UI-041
    @Ignore("Enable in Phase 6 Step 3") @Test fun `new SubCategory gets global minimum sortOrder minus 1`() = Unit

    // @spec DM-PROC-019
    @Ignore("Enable in Phase 6 Step 3") @Test fun `removeFromGroup resolves null emoji color and valueType to parent values`() = Unit

    // @spec DM-PROC-021
    @Ignore("Enable in Phase 6 Step 3") @Test fun `MetaCategory migration includes events of inheriting SubCategories`() = Unit

    // @spec DM-PROC-021
    @Ignore("Enable in Phase 6 Step 3") @Test fun `MetaCategory migration excludes events of SubCategories with explicit valueType`() = Unit
}
