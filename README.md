# VoteRewards

A PaperMC 1.21.11 voting plugin for **VotifierPlus** (also works with NuVotifier).

## Features
- Console-command rewards per vote (`rewards.yml`), with optional per-site extras
- Offline voters get their rewards queued until they next join
- `/vote` with clickable links to your vote sites (`config.yml`)
- Customisable chat broadcast when someone votes (MiniMessage)
- Discord webhook: instant per-vote embeds or a periodic "recent votes" summary
- `/voteadmin test <player> [service]` to test without a real vote site
- `/voteadmin reload`

## Building on GitHub
1. Push this folder to a GitHub repository (the `.github/workflows/build.yml` file must be included).
2. Open the **Actions** tab. The build runs automatically on every push.
3. Click the finished run and download the **VoteRewards** artifact; the jar is inside.

## Building locally
Requires JDK 21 and Maven: `mvn package`. The jar is in `target/`.

## Install
1. Install VotifierPlus and set it up with your vote sites.
2. Drop `VoteRewards-1.0.0.jar` in `plugins/` and restart.
3. Edit `plugins/VoteRewards/config.yml` and `rewards.yml`, then `/voteadmin reload`.

## Permissions
- `voterewards.vote` - use /vote (default: everyone)
- `voterewards.admin` - use /voteadmin (default: op)
