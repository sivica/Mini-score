/**
 * Neural Expressive NBA Box Score
 * High-fidelity, mobile-first basketball statistics interface
 */

class NBABoxScore {
    constructor(homeTeam, awayTeam) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.init();
    }

    init() {
        this.render();
        console.log('✨ Neural Expressive NBA Box Score initialized');
        console.log('Debug methods available:', {
            updateScore: 'nbaBoxScore.updateScore(team, points)',
            updatePlayer: 'nbaBoxScore.updatePlayerStat(team, playerIdx, stat, value)',
            getTeamStats: 'nbaBoxScore.getTeamStats(team)',
        });
    }

    calculateFG(fgm, fga) {
        if (fga === 0) return 0;
        return ((fgm / fga) * 100).toFixed(1);
    }

    calculateTeamStats(team) {
        const stats = {
            points: 0,
            rebounds: 0,
            assists: 0,
            fgm: 0,
            fga: 0,
        };

        team.players.forEach(player => {
            stats.points += player.points;
            stats.rebounds += player.rebounds;
            stats.assists += player.assists;
            stats.fgm += player.fgm;
            stats.fga += player.fga;
        });

        stats.fg = this.calculateFG(stats.fgm, stats.fga);
        return stats;
    }

    sortPlayersByPoints(team) {
        return [...team.players].sort((a, b) => b.points - a.points);
    }

    createPlayerCard(player) {
        const fg = this.calculateFG(player.fgm, player.fga);
        const card = document.createElement('div');
        card.className = 'player-card';
        card.innerHTML = `
            <div class="player-name">
                ${player.name}
                <span class="player-position">${player.position}</span>
            </div>
            <div class="player-stat ${player.points > 20 ? 'highlight' : ''}">
                <span class="stat-label">PTS</span>
                ${player.points}
            </div>
            <div class="player-stat">
                <span class="stat-label">REB</span>
                ${player.rebounds}
            </div>
            <div class="player-stat">
                <span class="stat-label">AST</span>
                ${player.assists}
            </div>
            <div class="player-stat">
                <span class="stat-label">FG%</span>
                ${fg}%
            </div>
        `;
        return card;
    }

    renderTeam(team, containerId) {
        const container = document.getElementById(containerId);
        const sortedPlayers = this.sortPlayersByPoints(team);
        const section = container.closest('.team-section');
        section.querySelector('.team-name').textContent = team.name;
        section.querySelector('.team-record').textContent = `${team.record} · Preseason · ${containerId === 'homePlayers' ? 'Home' : 'Away'}`;
        section.querySelector('.team-logo').textContent = team.code;
        
        // Clear existing players
        container.innerHTML = '';
        
        // Render sorted players
        sortedPlayers.forEach(player => {
            container.appendChild(this.createPlayerCard(player));
        });
    }

    updateScoreDisplay(team, scoreElementId) {
        const stats = this.calculateTeamStats(team);
        const scoreElement = document.getElementById(scoreElementId);
        scoreElement.textContent = stats.points;
    }

    render() {
        document.querySelector('.header-subtitle').textContent = `${GAME_DATA.competition} · ${GAME_DATA.status} · ${GAME_DATA.dateLabel}`;
        document.getElementById('gameVenue').textContent = GAME_DATA.venue;
        document.getElementById('homeQuarters').textContent = this.homeTeam.quarters.join(' · ');
        document.getElementById('awayQuarters').textContent = this.awayTeam.quarters.join(' · ');
        this.renderTeam(this.homeTeam, 'homePlayers');
        this.renderTeam(this.awayTeam, 'awayPlayers');
        this.updateScoreDisplay(this.homeTeam, 'homeScore');
        this.updateScoreDisplay(this.awayTeam, 'awayScore');
    }

    // Public API methods for dynamic updates

    updateScore(team, points) {
        const stats = this.calculateTeamStats(team);
        const diff = points - stats.points;
        team.players[0].points += diff;
        this.render();
        return stats.points;
    }

    updatePlayerStat(team, playerIdx, stat, value) {
        if (playerIdx >= 0 && playerIdx < team.players.length) {
            team.players[playerIdx][stat] = value;
            this.render();
        }
    }

    getTeamStats(team) {
        return this.calculateTeamStats(team);
    }

    getAllStats() {
        return {
            home: {
                team: this.homeTeam.name,
                stats: this.getTeamStats(this.homeTeam),
            },
            away: {
                team: this.awayTeam.name,
                stats: this.getTeamStats(this.awayTeam),
            },
        };
    }
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    // Instantiate with game data
    window.nbaBoxScore = new NBABoxScore(GAME_DATA.home, GAME_DATA.away);
});
