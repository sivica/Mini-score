package com.example

/** NBA image resources bundled with the APK so the box score works offline. */
object GameImageAssets {
    fun playerPortrait(player: Player): Int? = when (player.id) {
        "phi_23" -> R.drawable.player_phi_23
        "phi_21" -> R.drawable.player_phi_21
        "phi_7" -> R.drawable.player_phi_7
        "phi_0" -> R.drawable.player_phi_0
        "phi_77" -> R.drawable.player_phi_77
        "phi_1" -> R.drawable.player_phi_1
        "phi_45" -> R.drawable.player_phi_45
        "phi_14" -> R.drawable.player_phi_14
        "phi_35" -> R.drawable.player_phi_35
        "phi_8" -> R.drawable.player_phi_8
        "phi_12" -> R.drawable.player_phi_12
        "phi_33" -> R.drawable.player_phi_33
        "phi_11" -> R.drawable.player_phi_11
        "phi_19" -> R.drawable.player_phi_19
        "phi_00" -> R.drawable.player_phi_00
        "bkn_30" -> R.drawable.player_bkn_30
        "bkn_13" -> R.drawable.player_bkn_13
        "bkn_1" -> R.drawable.player_bkn_1
        "bkn_21" -> R.drawable.player_bkn_21
        "bkn_8" -> R.drawable.player_bkn_8
        "bkn_14" -> R.drawable.player_bkn_14
        "bkn_16" -> R.drawable.player_bkn_16
        "bkn_12" -> R.drawable.player_bkn_12
        "bkn_31" -> R.drawable.player_bkn_31
        "bkn_00" -> R.drawable.player_bkn_00
        "bkn_2" -> R.drawable.player_bkn_2
        "bkn_77" -> R.drawable.player_bkn_77
        "bkn_10" -> R.drawable.player_bkn_10
        "bkn_34" -> R.drawable.player_bkn_34
        "bkn_4" -> R.drawable.player_bkn_4
        else -> null
    }

    fun teamLogo(team: Team): Int? = when (team.code) {
        "PHI" -> R.drawable.team_phi
        "BKN" -> R.drawable.team_bkn
        else -> null
    }
}
