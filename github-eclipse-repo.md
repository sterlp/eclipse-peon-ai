## GitHub Pages Eclipse Update Site - Peon AI

## Current Status

### Build Artifacts
- **Update Site URL**: https://sterl.github.io/eclipse-peon-ai/
- **GitHub Pages Branch**: `gh-pages`
- **Build Output**: `releng/llmpeon-update-site/target/repository/`

### What's Working
- Maven Tycho build with `mvn clean verify`
- Feature project: `releng/llmpeon-feature/`
- p2 Update site: `releng/llmpeon-update-site/`
- LICENSE file (EPL-2.0) copied to META-INF during build
- Plugin renamed to "Peon AI" in metadata

### Files Created/Modified
| File | Description |
|------|-------------|
| `LICENSE` | EPL-2.0 license in project root |
| `releng/llmpeon-feature/` | Eclipse feature project |
| `releng/llmpeon-update-site/` | p2 repository project |
| `.github/workflows/maven.yml` | Build + deploy workflow |

---

## Known Issues

### 1. GitHub Action gh-pages Cleanup
**Problem**: The deploy step doesn't properly clean old files from gh-pages branch.

**Current Fix Applied** (in `.github/workflows/maven.yml`):
```bash
find . -maxdepth 1 ! -name '.git' ! -name '.' -exec rm -rf {} \;
```

**Testing**: Run locally first:
```bash
# Test build locally
mvn clean verify

# Verify output
ls releng/llmpeon-update-site/target/repository/
# Should contain: artifacts.jar, content.jar, p2.index, features/, plugins/
```

### 2. Testing Update Site Locally
Before pushing, test locally:

```bash
# 1. Build
mvn clean verify

# 2. Verify p2 content exists
ls releng/llmpeon-update-site/target/repository/
ls releng/llmpeon-update-site/target/repository/plugins/ | grep peon
ls releng/llmpeon-update-site/target/repository/features/

# 3. Test in Eclipse:
# Help > Install New Software > Add > Archive > select target/llmpeon-update-site-*.zip
```

---

## Usage

### Install from GitHub Pages
1. Open Eclipse
2. `Help > Install New Software`
3. Click `Add > Archive`
4. Download zip from: https://github.com/sterl/eclipse-peon-ai/releases
5. Or use direct URL: `https://sterl.github.io/eclipse-peon-ai/`

### Build Release
```bash
# Update version in pom.xml (remove -SNAPSHOT)
mvn clean verify

# Create release zip
cd releng/llmpeon-update-site/target/
zip -r peon-ai-update-site-1.0.0.zip repository/
```

---

## Future Improvements

1. **Eclipse Marketplace**: Submit to https://marketplace.eclipse.org/
2. **Automatic Version Bumping**: Use maven-hb-plugin or similar
3. **Sign JARs**: Add code signing for releases
4. **GitHub Releases**: Auto-create releases with upload artifact

---

## Commands Reference

```bash
# Full build
mvn clean verify

# Build only update site
mvn clean verify -pl releng/llmpeon-update-site -am

# Skip tests
mvn clean verify -DskipTests

# Local test - serve update site
cd releng/llmpeon-update-site/target/repository
python -m http.server 8080
# Then in Eclipse: http://localhost:8080
```
