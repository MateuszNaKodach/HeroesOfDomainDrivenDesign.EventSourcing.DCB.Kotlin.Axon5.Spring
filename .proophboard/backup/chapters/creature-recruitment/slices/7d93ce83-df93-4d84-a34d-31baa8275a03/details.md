## Scenarios (GWTs)

### 1. build for the first time

**Given**
NOTHING
**When**
:::element command
Build Dwelling
:::
**Then**
:::element event
Dwelling Built
:::

### 2. try to build already built

**Given**
:::element event
Dwelling Built
```yaml
dwellingId: portal-of-glory
```
:::
**When**
:::element command
Build Dwelling
```yaml
dwellingId: portal-of-glory
```
:::
**Then**
:::element event
Dwelling Built
```yaml
dwellingId: portal-of-glory
```
:::
