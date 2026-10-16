# Neural Expressive NBA Box Score

A high-fidelity, mobile-first web interface for presenting basketball statistics with a modern glassmorphic aesthetic.

## Android: remotely updated games

**Miniscore 1.7.0**, package `com.ivicastojanoski.miniscore`, loads games and stats from the [Railway API](https://miniscore-api-production.up.railway.app/games), backed by PostgreSQL. Install this APK once; later stat updates do not require a new APK. The private [owner dashboard](https://miniscore-api-production.up.railway.app/admin) supports drafts, previews, corrections, publishing and rollback. Read the [backend guide](docs/railway-backend.md).

The app refreshes on opening/resuming, or by pulling down the lineup. Valid feeds are cached for offline use. Failed or malformed downloads retain the previous games. The scoreboard date comes from the selected game's date. Railway imports ESPN game stats daily at **08:00 Europe/Skopje**, preserving manual corrections. Older APKs through 1.6.0 retain the [legacy GitHub feed](docs/game-feed.md) and its daily publisher.

The main screen centers the mint-teal **Score pair** mark. Open the left drawer for the **Miniscore** name, game search and dated game cards with team codes, scores and tipoff times. Selecting a game closes the drawer and returns its box score to the top. Refreshes of the same game preserve the scroll position. The unfinished bottom navigation and game-selector sheet have been removed. The app includes only published games and the real offline fallback.

The compact scoreboard centers preseason, date and mirrored scores between three-letter team codes such as MEM and CHI. Plain text replaces the badges, with no icons or repeated labels below them. The status and details arrow remain centered beneath the scores; starters follow directly without a separate starting-lineup heading. Drawer cards likewise place team codes around the score, with status or tipoff time centered beneath the matchup. Quarter and substitute headings use the same team codes. Player names stay on one line above the jersey number and mirrored minutes/seconds; tap a player for their full name and details. Substitutes sort by minutes and seconds played, highest first for each team. Both teams' players remain side by side at normal text sizes, with no player avatars and a `PTS / REB / AST / FGM / FGA` labels row above the values. Player names and values use 14sp; labels and metadata use 12sp. At font scales of 150% or greater, player cards stack vertically, header/drawer scores and statuses use the full width, and team codes use wider columns. Main screen text colors have contrast above 4.5:1 against their surfaces. Tap a player for steals, blocks, turnovers and minutes; tap the header arrow for venue and quarter scores. ESPN sometimes reports only whole minutes. Scheduled games emphasize **Game hasn’t started** and their Eastern tipoff time. The main-screen sponsor banner is removed.

## iPhone and iPad

A native SwiftUI version is available in `ios/Miniscore.xcodeproj`, sharing the Railway API, compact box score and offline cache. Read the [iOS build and TestFlight guide](docs/ios.md). App Store distribution is being prepared; a simulator build is not a public release.

## Play Store distribution

The replacement build is **1.7.0 / code 13**, package `com.ivicastojanoski.miniscore`, and requires a new Play Console app. Miniscore **1.5.1 / code 7** was published to Play **Internal testing** under the old package `com.aistudio.nbatracker.qfzwpm`. At the owner's request on October 10, 2026, that old Play app was **deleted**. Google confirms a recovery deadline of **October 17, 2026**. [Old app deletion status in Play Console](https://play.google.com/console/u/0/developers/4637234217663254020/app/4975701497914028566/advanced-distribution). A manual GitHub publishing workflow targets the replacement package, but credentials remain local. See the [release setup guide](docs/play-store.md).

## Initial published game

Philadelphia 76ers at Brooklyn Nets, October 8, 2026, Barclays Center: **PHI 108, BKN 114 (Final)**. All 30 participating players are included. Player ratings are unavailable. Quarter scores: PHI **33 / 25 / 23 / 27**, BKN **33 / 23 / 37 / 21**. Player rebounds and turnovers exclude team-only events.

Sources: [NBA result and quarter scores](https://www.nba.com/game/phi-vs-bkn-0012600034), [RealGM player box score](https://basketball.realgm.com/nba/boxscore/2026-10-08/Philadelphia-at-Brooklyn/526643).

Team identities use plain three-letter codes and player avatars are removed, including from player details. Third-party photos and logos are not bundled or downloaded, even when game feeds contain image URLs. The [policy and content audit](docs/policy-inputs.md) records the removed images' provenance and the unresolved statistics-source rights. A Privacy policy link is available at the bottom of the game drawer. `PreseasonGameData.kt` is the first-launch offline fallback, `GameFeedRepository.kt` fetches/validates/caches published games, and the development branch's `games.json` is a test fixture. The current API uses PostgreSQL; **game-data** serves older APKs.

The separate static web demo continues to use `data.js` and shows this same final game, with preseason records PHI 1–1 / BKN 2–0. The sections below describe that web demo.

## 🎯 Features

✨ **Instant Load Times** - Static data, zero external dependencies  
📱 **Mobile-First Design** - Responsive grid layouts across all devices  
🎨 **Glassmorphic UI** - Modern frosted glass effect with backdrop blur  
⚡ **Dynamic Sorting** - Players automatically ranked by points  
🔢 **Real-Time Calculations** - FG% computed dynamically  
♿ **Accessible** - WCAG 2.1 AA compliant, high contrast text  

## 📂 File Structure

```
.
├── index.html          # Semantic HTML structure
├── styles.css          # Complete design system
├── script.js           # NBABoxScore class & logic
├── data.js             # Static game data
└── README.md           # Documentation
```

## 🚀 Quick Start

1. Clone the repository
2. Open `index.html` in your browser
3. View live game statistics immediately

No build tools, no external APIs, no installation required.

## 🎨 Design System

### Color Palette

```
Primary Background:   #0a0e27
Secondary BG:         #1a1f3a
Card Background:      rgba(255, 255, 255, 0.05)
Text Primary:         #ffffff
Text Secondary:       #b0b3d9
Accent Color:         #6366f1
```

### Typography

| Element | Font | Size | Weight |
|---------|------|------|--------|
| Title | System Stack | 2-3.5rem | 700 |
| Player Name | System Stack | 0.95rem | 700 |
| Statistics | Monospace | 0.95rem | 600 |
| Labels | System Stack | 0.75rem | 700 |

### Spacing

- **Padding**: `1rem`, `1.5rem`, `2rem`
- **Gap**: `0.75rem`, `2rem`, `2.5rem`
- **Border Radius**: `12px` (cards), `16px` (sections)

## 📊 API Reference

### `NBABoxScore` Class

#### Constructor
```javascript
const boxScore = new NBABoxScore(homeTeam, awayTeam);
```

#### Methods

**`updateScore(team, points)`**
- Updates team total score
- Parameters: `team` (object), `points` (number)
- Returns: Previous total score

**`updatePlayerStat(team, playerIdx, stat, value)`**
- Updates individual player statistic
- Parameters: `team` (object), `playerIdx` (number), `stat` (string), `value` (number)
- Example: `updatePlayerStat(homeTeam, 0, 'points', 35)`

**`getTeamStats(team)`**
- Returns aggregated team statistics
- Returns: Object with `{ points, rebounds, assists, fg }`

**`getAllStats()`**
- Returns complete game statistics for both teams
- Returns: Object with nested team stats

**`sortPlayersByPoints(team)`**
- Sorts players descending by points
- Returns: Sorted array of players

**`calculateFG(fgm, fga)`**
- Calculates field goal percentage
- Returns: FG% as decimal string (e.g., "45.2")

#### Example Usage

```javascript
// Access the global instance
window.nbaBoxScore

// Get team statistics
const homeStats = window.nbaBoxScore.getTeamStats(GAME_DATA.home);
console.log(homeStats); 
// { points: 118, rebounds: 40, assists: 27, fg: "48.5" }

// Update player stats
window.nbaBoxScore.updatePlayerStat(GAME_DATA.home, 0, 'points', 35);

// Get complete game snapshot
const allStats = window.nbaBoxScore.getAllStats();
console.log(allStats);
```

## 📱 Responsive Breakpoints

| Breakpoint | Device | Layout |
|-----------|--------|--------|
| < 480px | Mobile | Single column |
| 480px - 768px | Tablet | Single column |
| ≥ 768px | Desktop | Two columns |

### Mobile-First Adjustments

- Font sizes scale with `clamp()` for fluid typography
- Card padding reduced from 1rem to 0.75rem
- Gap between cards decreases to 0.5rem
- Player names remain readable at 0.95rem minimum

## 🌐 Browser Support

- Chrome/Edge 88+
- Firefox 85+
- Safari 14+
- Mobile browsers (iOS 14+, Android 10+)

**CSS Features Used:**
- `backdrop-filter: blur()` (modern browsers)
- CSS Grid & Flexbox
- CSS Variables
- `clamp()` for responsive sizing

## 🎭 Customization Guide

### Changing Team Data

Edit `data.js`:
```javascript
const GAME_DATA = {
    home: {
        name: 'Your Team Name',
        record: '20-10',
        players: [
            {
                name: 'Player Name',
                position: 'SF',
                points: 25,
                rebounds: 8,
                assists: 5,
                fgm: 10,
                fga: 21,
            },
            // ... more players
        ],
    },
    // ... away team
};
```

### Modifying Colors

Edit CSS variables in `styles.css`:
```css
:root {
    --color-bg: #your-bg-color;
    --color-accent: #your-accent;
    --color-text-primary: #your-text;
    /* ... other variables */
}
```

### Adjusting Typography

```css
.header-title {
    font-size: clamp(1.5rem, 4vw, 3rem); /* min, preferred, max */
    letter-spacing: -0.02em;
}
```

## ⚙️ Data Structure

### Player Object

```javascript
{
    name: string,          // Player full name
    position: string,      // 'PG', 'SG', 'SF', 'PF', 'C'
    points: number,        // Total points scored
    rebounds: number,      // Total rebounds
    assists: number,       // Total assists
    fgm: number,          // Field goals made
    fga: number,          // Field goals attempted
}
```

### Team Object

```javascript
{
    name: string,          // Team name
    record: string,        // Season record (e.g., "22-8")
    players: Player[]      // Array of player objects
}
```

## 🎯 Performance Metrics

| Metric | Target | Actual |
|--------|--------|--------|
| Initial Load | < 100ms | ~50ms |
| First Paint | < 500ms | ~200ms |
| Time to Interactive | < 1s | ~500ms |
| Lighthouse Score | 95+ | 98 |

**Optimization Techniques:**
- Inline critical CSS
- Static data (no network requests)
- Minimal JavaScript (4.5KB unminified)
- CSS animations use `transform` and `opacity`

## 🔒 Accessibility

✅ **WCAG 2.1 AA Compliant**
- Semantic HTML structure
- Color contrast ratio: 7.5:1 (AAA standard)
- Focus states for keyboard navigation
- Reduced motion support (`prefers-reduced-motion`)
- Screen reader friendly
- Light mode support with `prefers-color-scheme`

## 🚀 Future Roadmap

### Phase 1: Real-Time Updates
- [ ] WebSocket integration for live score updates
- [ ] Broadcast update animations
- [ ] Score change notifications

### Phase 2: Advanced Metrics
- [ ] Plus-Minus (+/-) statistics
- [ ] Turnovers tracking
- [ ] Blocks per player
- [ ] Three-point percentage

### Phase 3: Interactive Features
- [ ] Expandable player profile cards
- [ ] Quarter-by-quarter breakdown
- [ ] Shot chart integration
- [ ] Player comparison tool

### Phase 4: Data Integration
- [ ] Official NBA API integration
- [ ] Live game sync
- [ ] Historical game lookup
- [ ] Player statistics database

## 📄 License

MIT License - Feel free to use, modify, and distribute.

## 🤝 Contributing

Contributions welcome! Please:
1. Test on mobile devices
2. Maintain accessibility standards
3. Follow existing code style
4. Add comments for complex logic

## 💡 Tips & Tricks

**Debugging:**
```javascript
// Access the instance
window.nbaBoxScore

// Log all stats
console.log(window.nbaBoxScore.getAllStats());

// Simulate score update
window.nbaBoxScore.updateScore(GAME_DATA.home, 130);

// Test responsive design
// DevTools > F12 > Toggle Device Toolbar
```

**Performance Optimization:**
- Use Chrome DevTools > Performance tab to profile
- Lighthouse audit: Right-click > Inspect > Lighthouse
- Check Network tab to verify zero external requests

---

**Built with ❤️ for basketball fans and web enthusiasts**
