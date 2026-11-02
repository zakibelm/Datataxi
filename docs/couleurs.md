# Couleurs DataTaxi (application et tableau de bord du Google Sheet)

Les deux interfaces utilisent les mêmes couleurs. Dans l'application, elles sont écrites dans
`app/src/main/java/com/datataxi/ui/Theme.kt` et `PredictionScreen.kt` (poids).

| Usage | Couleur | Application | Google Sheet |
|---|---|---|---|
| Principale (titres, onglet actif, courbe de réussite) | `#6750A4` | `primary` | titres des sections et des tableaux, onglet, courbe |
| Bandeau clair | `#EADDFF` | `primaryContainer` (carte du haut) | titres de section |
| Ligne en cours | `#E8DEF8` | `secondaryContainer` | début de la plage dans la grille |
| Fond de carte | `#E7E0EC` | `surfaceVariant` | zones habituelles |
| Poids Faible | `#FFF1C2` | grille, légende | grille, légende |
| Poids Moyen | `#FFCF5C` | grille, légende | grille, légende, opportunité |
| Poids Fort | `#F28C38` | grille, légende | grille, légende |
| Poids Très fort | `#C62828` (texte blanc) | grille, légende | grille, légende |
| Écart en hausse / en baisse | `#1B7F2A` / `#C62828` | niveau de confiance | rapport de fiabilité |
| Hasard (pointillé) | `#8A8A8A` | graphique | graphique |
