# Journal des itérations — tableau de bord DataTaxi

Méthode de mesure (réutilisable) : on rejoue les jours du 2026-10-20 au 2026-11-02 un par un ;
pour chaque jour, le modèle ne voit que les jours précédents, puis on compare aux vrais appels.
Données : Google Sheet nettoyé (6 657 lignes ; 5 472 appels utilisables avec zone de départ,
dont 2 078 archives sept.–oct.).

## Itération 1 — v0.8.0 : parts en % (journal de la tablette)
- Changement : onglet Prédictions, tableau heure × zone, % = part des appels du même jour de la
  semaine (± 20 min, poids 3-2-1), repli sur tous les jours sous 8 appels.
- Mesure : recommandation n°1 juste 49,3 % ; zones recommandées autres que 1 et 6 justes 18,7 %.
- Leçon : **perd contre une constante** (« toujours zone 1 » : 53,5 % ; « toujours 1, 6, 12 » : 64 %
  des appels couverts). Le top 3 en % ne fait que redire que les zones 1 et 6 sont toujours actives.
  Ne pas refaire de classement par parts.

## Itération 2 — v0.9.0 : récurrences sur tout l'historique
- Changement structurel : l'écran affiche les zones qui reviennent au moins 2 journées (même jour
  de la semaine, même tranche de 10 min ± 10 min), avec un poids, calculées sur tout l'historique
  (n8n « DataTaxi - Historique pour l'app ») + le journal de la tablette.
- Mesure (calibration du poids, ± 10 min) : chance d'un appel dans la zone le jour même
  0 jour 4,1 % · 1 jour 22,8 % · 2 jours 41,5 % · 3 jours 53,0 % · 4 jours 59,8 % · 5 jours 58,3 % (n = 12).
  → Faible = 2, Moyen = 3, Fort = 4, Très fort = 5 et plus.
- Mesure (comparaison) : recommandation n°1 juste 51,7 % (contre 49,3 %) ; zones autres que 1 et 6
  justes 29,5 % (contre 18,7 %, + 58 %) ; mais une recommandation dans 50 % des tranches seulement.
- Leçon : la récurrence est le bon signal (× 10 à × 15 par rapport au hasard). Tolérance ± 10 min
  indispensable : en tranche stricte, 2 jours ne donnent que 30,6 %.
- Variantes écartées : poids au taux (jours/jours observés) — moins bien séparé (40 / 45 / 56 %) ;
  tranche stricte sans tolérance — couverture 37 %.

## Itération 3 — tableau de bord Google Sheet : carte de décision « Opportunité »
- Diagnostic : la ligne « Maintenant » listait jusqu'à 5 zones (le chauffeur hésite) ou rien
  (« Aucune récurrence » la moitié du temps). Et elle commençait presque toujours par la zone 1.
- Découverte : sur une fenêtre de 30 min (maintenant − 10 min à + 20 min), la zone 1 a un appel
  86,3 % du temps. « Un appel aura lieu dans la zone » est saturé par les zones 1 et 6 : tout le
  monde les connaît. La valeur est dans la zone moins évidente.
- Mesure (toute l'historique rejoué jour par jour depuis le 2026-09-08, 2 107 tranches jugées,
  succès = appel dans la zone entre − 10 et + 20 min ; zones hors 1 et 6) :
  · actuel (1re zone hors 1/6 de « Maintenant ») : réponse 20,6 %, juste 48,5 %, succès/tranche 10,0 %
  · **regard sur les 20 prochaines minutes** : réponse 27,0 %, juste 47,8 %, succès/tranche 12,9 % (+ 29 %)
  · taux jours/jours observés : juste 41,9 % — écarté
  · écart avec l'habitude de la zone (lift) : juste 36,8 % — écarté
  · 3 journées minimum : juste 55 % mais réponse 4 à 7 % — écarté (trop rare)
  · référence au hasard « toujours zone 12 » : juste 39,4 %
- Changement livré : la ligne « Maintenant » devient deux lignes :
  « Zones habituelles » (les 2 zones les plus actives de l'historique, avec leur récurrence)
  et « Opportunité, 20 prochaines minutes » (une seule zone hors des 2 habituelles, la plus
  récurrente, la plus proche en cas d'égalité, avec heure, poids et réussite mesurée).
- Vérification : 5 moments (dont 3 saisis dans la case de référence) identiques à un calcul
  Python indépendant.
- Leçons : ne plus mesurer la réussite des zones 1/6 (toujours vraie) ; les classements au taux et
  au lift perdent contre le simple nombre de journées.
- Piège Sheets : dans LET, des noms comme c1, c2 sont des adresses de cellule → #NAME?.

## Pistes suivantes (une à la fois)
- Mesurer « Très fort » quand l'historique dépassera 8 semaines (n trop petit aujourd'hui).
- Mesurer la concurrence (nombre de véhicules qui prennent des appels dans la zone à cette
  heure) : une zone avec peu de véhicules pour ses appels vaut plus qu'une zone saturée.
