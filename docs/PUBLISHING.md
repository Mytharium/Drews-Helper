# Publishing Drew's Helper

RuneLite Plugin Hub publication uses this plugin repo plus one pull request to `runelite/plugin-hub`.

## Local Integration

Run this from the repo root:

```bat
gradlew.bat run
```

The Gradle `run` task launches RuneLite in developer mode and loads `com.drewshelper.DrewsHelperPlugin` through `ExternalPluginManager.loadBuiltin`.

## Plugin Repository

The repo needs to be public, buildable, licensed, and include `runelite-plugin.properties`. Current metadata:

```properties
displayName=Drew's Helper
author=Mytharium
description=Pathing and teleport helper for RuneLite.
tags=path,pathing,route,teleport,quest,helper
version=
plugins=com.drewshelper.DrewsHelperPlugin
build=standard
```

Before submitting to Plugin Hub, commit and push this repo, then copy the full 40-character commit hash.

## Plugin Hub PR

Fork `https://github.com/runelite/plugin-hub` and add one file:

```text
plugins/drews-helper
```

Contents:

```properties
repository=https://github.com/Mytharium/Drews-Helper.git
commit=<full 40-character commit sha from this repo>
```

Open a PR against `runelite/plugin-hub:master` with only that file changed. If CI asks for changes, update this repo, push another commit, and update the `commit=` hash in the Plugin Hub PR.

## Compliance Boundary

This plugin should calculate routes and highlight real widgets or items. It should not send clicks, add server-action menu entries, or perform teleports automatically.
