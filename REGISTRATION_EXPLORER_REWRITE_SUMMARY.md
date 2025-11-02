# RegistrationExplorer JavaFX to Swing Rewrite - Summary

## Overview

Successfully rewrote `RegistrationExplorerPanel.java` to remove all JavaFX dependencies and use pure Swing components instead. All functionality has been preserved with a similar appearance.

## What Was Changed

### File Modified
- `src/main/java/net/preibisch/mvrecon/fiji/spimdata/explorer/registration/RegistrationExplorerPanel.java`

### Key Changes

#### 1. Removed JavaFX Components
**Before (JavaFX):**
- `TreeTableView<RegistrationExplorerRow>` - Main tree-table component
- `TreeItem<RegistrationExplorerRow>` - Tree hierarchy nodes
- `TreeTableColumn` - Column definitions
- `JFXPanel` - Swing-JavaFX bridge
- `Scene`, `Group` - JavaFX scene graph
- `Platform.runLater()` - JavaFX threading
- `TextField` - JavaFX text input (for inline editing)
- `Callback`, `EventHandler` - JavaFX event patterns
- `ObservableValue`, `ObservableList` - JavaFX observable collections

**After (Pure Swing):**
- `JTable` with custom `AbstractTableModel`
- Flat list of rows with type tracking (GROUP vs REGISTRATION)
- Standard Swing column model
- Direct Swing components (no bridge needed)
- Standard Swing event handling
- `JTextField` - Swing text input
- Standard Java listener patterns
- Standard Java collections (List, Set, Map)

#### 2. New Hierarchical Table Architecture

**HierarchicalRegistrationTableModel** (custom AbstractTableModel):
- Maintains flat list of visible rows
- Tracks which groups are expanded/collapsed using `Set<ViewId>`
- Dynamically rebuilds row list when groups expand/collapse
- 13 columns: Name + m00-m33 (3x4 affine matrix)

**TableRow** (inner class):
- Encapsulates row data (GROUP or REGISTRATION type)
- Stores ViewDescription reference
- Stores transform index for REGISTRATION rows

**Expand/Collapse Mechanism:**
- Click on Name column of GROUP row toggles expansion
- Visual indicators: ▶ (collapsed) / ▼ (expanded)
- State persists across view selection changes

#### 3. Custom Cell Rendering

**HierarchicalCellRenderer** (extends DefaultTableCellRenderer):
- **GROUP rows:**
  - Bold font
  - Light purple background (230, 230, 250)
  - Expand/collapse arrows (▶/▼) in Name column
  - Format: "ViewSetup: X, TP: Y"

- **REGISTRATION rows:**
  - Plain font
  - White background
  - Indentation ("    ") in Name column
  - Shows transform name or matrix values

- **Matrix columns:**
  - Center-aligned
  - 6 decimal places (%.6f)

#### 4. Inline Editing

**HierarchicalCellEditor** (extends DefaultCellEditor):
- All REGISTRATION row cells are editable
- Name column: Edit transformation name
- Matrix columns (m00-m33): Edit matrix values
- Automatic indentation removal during editing
- Updates ViewRegistration and triggers BDV refresh
- Error handling for invalid number format

#### 5. Copy/Paste/Delete Operations

All operations preserved from JavaFX version:

**Copy:**
- Selects only REGISTRATION rows
- Stores ViewTransform copies in cache
- Ignores GROUP row selections

**Paste (3 modes):**
- **Before** (type 0): Insert before selected registrations
- **Replace** (type 1): Replace selected registrations
- **After** (type 2): Insert after selected registrations
- Works across multiple ViewDescriptions
- Maintains sort order of insertions

**Delete:**
- Removes selected REGISTRATION rows
- Ensures at least one transform remains (adds identity if empty)
- Ignores GROUP row selections

#### 6. Visual Improvements

- Row height: 22 pixels (better readability)
- Name column width: 300 pixels
- Matrix columns width: 80 pixels each
- Font size: 11pt
- Multiple row selection supported
- Right-click context menu
- Mouse click handling for expand/collapse
- **Auto-expand first entry:** First GROUP row automatically expands on startup

## Functionality Preserved

✅ Hierarchical display of ViewDescriptions and Registrations
✅ Expand/collapse groups
✅ Inline editing of transformation names
✅ Inline editing of matrix values (m00-m33)
✅ Copy/paste/delete operations
✅ Multiple selection support
✅ Context menu (right-click)
✅ Integration with BDV (updates on changes)
✅ ViewDescription selection updates from ViewSetupExplorer

## Code Statistics

- **Before:** 740 lines (with JavaFX)
- **After:** 685 lines (pure Swing)
- **Reduction:** 55 lines (7.4% smaller)
- **No external dependencies added**

## Compilation

✅ **Successfully compiles** with no errors or warnings
✅ **No JavaFX imports remain** in registration package
✅ **RegistrationExplorer.java** works without modification

## Testing Recommendations

When testing the new implementation, verify:

1. **Expand/Collapse:**
   - Click GROUP rows to expand/collapse
   - Arrow icons update correctly (▶/▼)
   - Child REGISTRATION rows appear/disappear

2. **Editing:**
   - Double-click or press F2 to edit cells
   - Edit transformation names (Name column)
   - Edit matrix values (m00-m33 columns)
   - Invalid numbers show error dialog
   - BDV updates after edits

3. **Selection:**
   - Single and multiple row selection
   - Shift+Click for range selection
   - Ctrl+Click for non-contiguous selection

4. **Copy/Paste/Delete:**
   - Right-click context menu appears
   - Copy selected registrations
   - Paste before/replace/after
   - Delete registrations
   - Operations work across multiple views

5. **ViewDescription Updates:**
   - Select different views in ViewSetupExplorer
   - Table updates to show selected views
   - Expanded state persists for same views

## JavaFX Dependencies Removed from pom.xml

✅ **COMPLETED** - JavaFX dependencies have been completely removed from `pom.xml`.

The following dependencies were removed:
- `org.openjfx:javafx-controls`
- `org.openjfx:javafx-base`
- `org.openjfx:javafx-swing`

The project now compiles successfully without any JavaFX dependencies.

## Known Differences from JavaFX Version

### Visual Differences:
1. **Tree lines:** No tree connection lines (JavaFX had these). Now uses indentation only.
2. **Arrows:** Unicode arrows (▶/▼) instead of JavaFX styled arrows
3. **Background:** Slightly different shade of purple for GROUP rows
4. **Selection:** Standard Swing selection color instead of JavaFX color

### Behavioral Differences:
1. **Focus:** Swing focus behavior differs slightly from JavaFX
2. **Tab key:** Standard Swing tab navigation (different from JavaFX)
3. **Mouse events:** Swing mouse handling may feel slightly different

### Improvements:
1. **Simpler codebase:** No JavaFX threading concerns
2. **Faster startup:** No JavaFX platform initialization
3. **Consistent with project:** Matches InterestPointExplorer's Swing implementation
4. **Better performance:** Native Swing rendering, no bridge overhead

## Architecture Notes

### Why This Design?

**Flat row model vs Tree model:**
- Simpler to implement than JTree with custom columns
- Better performance for our use case (small datasets)
- Easier to maintain and debug
- Direct access to any row by index

**Expand/collapse state:**
- Stored in `Set<ViewId>` for O(1) lookup
- Persists across data updates
- Could be extended to save/restore state

**Custom renderers:**
- Separates display logic from data model
- Easy to modify appearance
- Standard Swing patterns

## Future Enhancements (Optional)

Possible improvements if needed:

1. **Visual tree lines:** Could add custom painting for tree connection lines
2. **Keyboard expand/collapse:** Add arrow key handling for +/- keys
3. **Double-click expand:** Toggle on double-click (in addition to single-click)
4. **Sort columns:** Add column sorting (though may conflict with hierarchy)
5. **Row filtering:** Add search/filter functionality
6. **Drag and drop:** Reorder transformations via drag-drop
7. **Undo/Redo:** Add operation history

## Summary

The RegistrationExplorer has been successfully modernized to use pure Swing components, eliminating the JavaFX dependency while preserving all functionality. The new implementation is simpler, more consistent with the rest of the codebase, and easier to maintain.

**Status:** ✅ Complete, compiled, and ready for testing
**Branch:** splitCorr
**Commit:** Not committed (per user request)
