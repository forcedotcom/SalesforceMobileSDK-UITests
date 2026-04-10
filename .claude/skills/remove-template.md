---
name: remove-template
description: Check UITests repository after removing a template
type: skill
---

# Check UITests Repository After Template Removal

The UITests repository test code works dynamically and doesn't hardcode template names, but **GitHub Actions workflows do hardcode app types** in their test matrices.

## Background

**Test Code (No Changes Needed)**:
- **AppGenerator.kt** - Dynamically constructs template URLs
- **AppType.kt** - Defines generic app types (NATIVE, HYBRID_LOCAL, etc.)
- **Test code** - Uses `test_force.js` from Package repo which reads `templates.json`

**GitHub Actions Workflows (May Need Changes)**:
- Workflow files explicitly list app types in matrix definitions
- If you remove the **last template** of an app type, you must update workflows

## When to Check

**Required** if you're removing the last template of an app type (e.g., removing both `iOSNativeTemplate` and `AndroidNativeTemplate` eliminates the `native` app type).

**Not needed** if other templates with the same app type remain.

## Steps

### 1. Check if Last Template of AppType

Determine if you're removing the last template with a specific app type by checking `templates.json` in the Templates repo.

Example: If removing `iOSNativeTemplate` (native) and `iOSNativeSwiftTemplate` (native_swift) still exists, no workflow changes needed.

### 2. Update Workflow Files (if last of app type)

**Files to update**:
- `.github/workflows/nightly.yaml`
- `.github/workflows/pr.yaml`

#### 2.1. Update nightly.yaml

**File**: `.github/workflows/nightly.yaml`

Find all matrix definitions and remove the app type:

```yaml
jobs:
  android-base:
    strategy:
      matrix:
        app: [native, native_kotlin, hybrid_local, hybrid_remote, react_native, ...]
        #     ^^^^^^ Remove if this is the last 'native' template

  android-sfdx:
    strategy:
      matrix:
        app: [native, hybrid_local, react_native]
        #     ^^^^^^ Remove here too

  ios-base-legacy:
    strategy:
      matrix:
        app: [native, native_swift, hybrid_local, hybrid_remote, react_native, ...]
        #     ^^^^^^ Remove if last 'native' template

  ios-base:
    strategy:
      matrix:
        app: [native, native_swift, hybrid_local, ...]
        #     ^^^^^^ Remove here too

  ios-sfdx:
    strategy:
      matrix:
        app: [native, hybrid_local, react_native]
        #     ^^^^^^ Remove here too
```

#### 2.2. Update pr.yaml

**File**: `.github/workflows/pr.yaml`

Remove the app type from both iOS and Android PR matrices:

```yaml
jobs:
  ios-pr:
    strategy:
      matrix:
        app: [native, hybrid_local, react_native]
        #     ^^^^^^ Remove if last of this app type

  android-pr:
    strategy:
      matrix:
        app: [native, hybrid_local, react_native]
        #     ^^^^^^ Remove here too
```

### 3. Verify No Other References

```bash
cd SalesforceMobileSDK-UITests
grep -r "native" .github/workflows/
# Or search for your specific app type
```

## Validation

### Checklist

- [ ] Determined if removing last template of an app type
- [ ] If last of app type:
  - [ ] nightly.yaml updated (all 5 matrix definitions)
  - [ ] pr.yaml updated (2 matrix definitions)
- [ ] No other references: `grep -r "<appType>" .github/workflows/`

## Commit (if changes made)

```bash
git add -A
git commit -m "Remove <appType> from CI test matrices

Updated after removing last template with appType '<appType>' from Templates repo:
- nightly.yaml: Removed from android-base, android-sfdx, ios-base-legacy, ios-base, ios-sfdx matrices
- pr.yaml: Removed from ios-pr and android-pr matrices"

git push origin <branch-name>
```

## Result

- **No changes needed** if other templates with the same app type remain
- **Workflow updates required** if removing the last template of an app type

## Example: Removing Native AppType

If removing both `iOSNativeTemplate` and `AndroidNativeTemplate` (the only `native` app type templates):

```bash
cd SalesforceMobileSDK-UITests
git checkout -b remove-native-apptype

# Edit .github/workflows/nightly.yaml
# Remove 'native' from all 5 matrix definitions:
# - android-base: line ~15
# - android-sfdx: line ~26  
# - ios-base-legacy: line ~38
# - ios-base: line ~51
# - ios-sfdx: line ~63

# Edit .github/workflows/pr.yaml
# Remove 'native' from both matrices:
# - ios-pr: line ~36
# - android-pr: line ~48

git add .github/workflows/
git commit -m "Remove native appType from CI matrices"
git push origin remove-native-apptype
```
