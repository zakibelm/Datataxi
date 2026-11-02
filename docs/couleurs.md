# Couleurs DataTaxi (application et tableau de bord du Google Sheet)

Thème bleu marine, le même dans les deux interfaces. Dans l'application, les couleurs sont écrites
dans `app/src/main/java/com/datataxi/ui/Theme.kt` et `PredictionScreen.kt` (poids).

| Usage | Couleur | Application | Google Sheet |
|---|---|---|---|
| Principale (titres de tableau, onglet actif, courbe de réussite) | `#1F3864` bleu marine, texte blanc | `primary` | en-têtes des tableaux, bloc Filtres, onglet, courbe |
| Bandeau clair | `#D8EAFF` bleu clair | carte du haut, puces sélectionnées | titres de section, début de la plage |
| Fond de carte | `#EDF1F6` gris bleuté | cartes | zones habituelles |
| Poids Faible | `#FFF1C2` | grille, légende | grille, légende |
| Poids Moyen | `#FFCF5C` | grille, légende | grille, légende, opportunité |
| Poids Fort | `#F28C38` | grille, légende | grille, légende |
| Poids Très fort | `#C62828`, texte blanc | grille, légende | grille, légende |
| Écart en hausse / en baisse | `#1B7F2A` / `#C62828` | niveau de confiance | rapport de fiabilité |
| Hasard (pointillé) | `#8A8A8A` | graphique | graphique |
