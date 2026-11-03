/**
 * DataTaxi : calcul des récurrences par zone, pour le « Tableau de bord ».
 *
 * Une récurrence = une zone qui a eu au moins un appel dans la même tranche de 10 minutes
 * (à 10 minutes près), le même jour de la semaine, plusieurs journées différentes.
 * Le script lit la feuille des appels, calcule tout une fois, et écrit une petite feuille
 * de résultats. Le tableau de bord ne fait que lire ces résultats : il reste rapide même
 * avec des mois d'historique.
 *
 * Installation (une seule fois) :
 *   1. Dans le classeur : Extensions > Apps Script, coller ce fichier, enregistrer.
 *   2. Choisir la fonction « installer » puis Exécuter, et accepter l'autorisation.
 *      Elle calcule tout de suite et programme un recalcul toutes les heures.
 *   Ensuite : menu « DataTaxi > Recalculer maintenant » dans le classeur, au besoin.
 */

const SOURCE_SHEET = 'Untitled';
const RESULT_SHEET = 'Récurrences (script)';
const DASHBOARD_SHEET = 'Tableau de bord';
const FIABILITE_SHEET = 'Fiabilité (script)';

/** Journées minimum pour afficher une récurrence (plage nommée « Seuils_poids » du tableau de bord). */
const DEFAULT_MIN_DAYS = 2;
const TOP_ZONES = 15;
const SLOTS_PER_DAY = 144; // tranches de 10 minutes

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('DataTaxi')
    .addItem('Recalculer maintenant', 'calculerRecurrences')
    .addToUi();
}

/** Calcule une première fois et programme le recalcul toutes les heures. */
function installer() {
  ScriptApp.getProjectTriggers()
    .filter(t => t.getHandlerFunction() === 'calculerRecurrences')
    .forEach(t => ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('calculerRecurrences').timeBased().everyHours(1).create();
  calculerRecurrences();
}

function calculerRecurrences() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const source = ss.getSheetByName(SOURCE_SHEET);
  if (!source) throw new Error('Feuille introuvable : ' + SOURCE_SHEET);

  // Valeurs affichées : les dates et les heures arrivent toujours en texte, quel que soit leur format.
  const values = source.getDataRange().getDisplayValues();
  const minDays = lireMinDays_(ss);
  const result = calculer_(values, minDays);
  ecrire_(ss, result);
  ecrireFiabilite_(ss, fiabilite_(values, lireSeuils_(ss)));
}

/**
 * Seuils des poids (journées minimum pour Faible, Moyen, Fort, Très fort), lus dans la plage
 * nommée « Seuils_poids » du tableau de bord : elle suit les cases si des lignes sont ajoutées.
 */
function lireSeuils_(ss) {
  const def = [DEFAULT_MIN_DAYS, 3, 4, 5];
  const range = ss.getRangeByName('Seuils_poids');
  if (!range) return def;
  const v = range.getValues().map(r => Number(r[0]));
  return v.every((x, i) => x >= 1 && (i === 0 || x > v[i - 1])) ? v : def;
}

function lireMinDays_(ss) {
  return lireSeuils_(ss)[0];
}

/**
 * Cœur du calcul, sans dépendance à Google (testable ailleurs).
 * @param {string[][]} values feuille des appels, ligne d'en-tête comprise.
 * @return {{rows: Array, totalCalls: number, firstDate: string, lastDate: string,
 *           coveredDays: number, observedDays: number[], topZones: string[]}}
 */
function calculer_(values, minDays) {
  const header = values[0].map(h => String(h).trim());
  const col = name => {
    const i = header.indexOf(name);
    if (i < 0) throw new Error('Colonne introuvable : ' + name);
    return i;
  };
  const cDate = col('trip_date'), cTime = col('trip_time'), cZone = col('departure_zone');
  const cStatus = col('current_status'), cTreatment = col('source_treatment'), cDest = col('destination_zone');

  // jour (1-7) -> tranche -> zone -> ensemble des dates
  const index = {};
  const daysByWeekday = {};
  const allDays = new Set();
  const callsByZone = {};
  let totalCalls = 0;

  for (let r = 1; r < values.length; r++) {
    const row = values[r];
    const date = String(row[cDate] || '').trim();
    const time = String(row[cTime] || '').trim();
    const status = String(row[cStatus] || '').trim();
    // Appel en attente : compté pour la zone où il était déclaré (après « vers »), comme les archives.
    const dest = String(row[cDest] || '').trim();
    const zone = (/^pris/.test(status) && /^\d+$/.test(dest)) ? dest : String(row[cZone] || '').trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{1,2}:\d{2}:\d{2}$/.test(time)) continue;
    if (!/^\d+$/.test(zone)) continue;
    if (status === 'historique_xlsx') {
      if (String(row[cTreatment] || '').trim() === 'Exclu') continue;
    } else if (!/^(recu|pris)/.test(status)) continue;

    const parts = time.split(':').map(Number);
    const slot = Math.floor((parts[0] * 60 + parts[1]) / 10);
    const weekday = weekdayOf_(date);

    totalCalls++;
    allDays.add(date);
    (daysByWeekday[weekday] = daysByWeekday[weekday] || new Set()).add(date);
    callsByZone[zone] = (callsByZone[zone] || 0) + 1;

    // L'appel compte pour sa tranche et les deux voisines (à 10 minutes près).
    for (let s = slot - 1; s <= slot + 1; s++) {
      if (s < 0 || s >= SLOTS_PER_DAY) continue;
      const bySlot = (index[weekday] = index[weekday] || {});
      const byZone = (bySlot[s] = bySlot[s] || {});
      (byZone[zone] = byZone[zone] || new Set()).add(date);
    }
  }

  const rows = [];
  Object.keys(index).forEach(w => {
    Object.keys(index[w]).forEach(s => {
      Object.keys(index[w][s]).forEach(z => {
        const days = index[w][s][z].size;
        if (days >= minDays) rows.push([Number(w), Number(s), z, days]);
      });
    });
  });
  rows.sort((a, b) => a[0] - b[0] || a[1] - b[1] || b[3] - a[3] || Number(a[2]) - Number(b[2]));

  const sortedDays = Array.from(allDays).sort();
  const observedDays = [1, 2, 3, 4, 5, 6, 7].map(w => (daysByWeekday[w] ? daysByWeekday[w].size : 0));
  const topZones = Object.keys(callsByZone)
    .sort((a, b) => callsByZone[b] - callsByZone[a] || Number(a) - Number(b))
    .slice(0, TOP_ZONES);

  return {
    rows: rows,
    totalCalls: totalCalls,
    firstDate: sortedDays[0] || '',
    lastDate: sortedDays[sortedDays.length - 1] || '',
    coveredDays: sortedDays.length,
    observedDays: observedDays,
    topZones: topZones,
  };
}

/** 1 = lundi ... 7 = dimanche, comme WEEKDAY(date; 2). */
function weekdayOf_(isoDate) {
  const p = isoDate.split('-').map(Number);
  const d = new Date(Date.UTC(p[0], p[1] - 1, p[2])).getUTCDay(); // 0 = dimanche
  return d === 0 ? 7 : d;
}

function ecrire_(ss, res) {
  let sh = ss.getSheetByName(RESULT_SHEET);
  if (!sh) sh = ss.insertSheet(RESULT_SHEET);
  sh.clearContents();

  const needRows = Math.max(res.rows.length + 1, 20);
  if (sh.getMaxRows() < needRows) sh.insertRowsAfter(sh.getMaxRows(), needRows - sh.getMaxRows());
  if (sh.getMaxColumns() < 11) sh.insertColumnsAfter(sh.getMaxColumns(), 11 - sh.getMaxColumns());

  // Zones en texte, pour que « 12 » du tableau de bord corresponde toujours à « 12 » ici.
  sh.getRange('C:C').setNumberFormat('@');
  sh.getRange('K:K').setNumberFormat('@');

  sh.getRange(1, 1, 1, 4).setValues([['Jour (1 = lundi)', 'Tranche (0 = 00:00)', 'Zone', 'Journées']]);
  if (res.rows.length) {
    sh.getRange(2, 1, res.rows.length, 4).setValues(res.rows.map(r => [r[0], r[1], String(r[2]), r[3]]));
  }

  const tz = ss.getSpreadsheetTimeZone();
  sh.getRange('H1:I5').setValues([
    ['Dernier calcul', Utilities.formatDate(new Date(), tz, 'yyyy-MM-dd HH:mm')],
    ['Appels dans l\'historique', res.totalCalls],
    ['Premier jour', res.firstDate],
    ['Dernier jour', res.lastDate],
    ['Journées couvertes', res.coveredDays],
  ]);
  sh.getRange('H7:I7').setValues([['Jour (1 = lundi)', 'Journées observées']]);
  sh.getRange(8, 8, 7, 2).setValues(res.observedDays.map((n, i) => [i + 1, n]));
  sh.getRange('K1').setValue('Zones les plus actives');
  sh.getRange(2, 11, TOP_ZONES, 1).clearContent();
  if (res.topZones.length) {
    sh.getRange(2, 11, res.topZones.length, 1).setValues(res.topZones.map(z => [String(z)]));
  }
}

/* ------------------------------------------------------------------------------------------
 * Fiabilité : chaque journée est rejouée comme si on y était. Les récurrences sont calculées
 * avec les seules journées précédentes, puis comparées aux appels réellement observés ce jour-là.
 * On ne juge que les tranches où la journée a des données (au moins un appel à 10 minutes près) :
 * une tablette éteinte ne compte pas comme une prédiction ratée.
 * ---------------------------------------------------------------------------------------- */

const LEVELS = ['Faible', 'Moyen', 'Fort', 'Très fort'];
const WEEKDAY_NAMES = ['', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche'];

/** Appels retenus : {date, weekday, slot, zone}, triés par date. */
function appels_(values) {
  const header = values[0].map(h => String(h).trim());
  const col = name => {
    const i = header.indexOf(name);
    if (i < 0) throw new Error('Colonne introuvable : ' + name);
    return i;
  };
  const cDate = col('trip_date'), cTime = col('trip_time'), cZone = col('departure_zone');
  const cStatus = col('current_status'), cTreatment = col('source_treatment'), cDest = col('destination_zone');
  const out = [];
  for (let r = 1; r < values.length; r++) {
    const row = values[r];
    const date = String(row[cDate] || '').trim();
    const time = String(row[cTime] || '').trim();
    const status = String(row[cStatus] || '').trim();
    // Appel en attente : compté pour la zone où il était déclaré (après « vers »), comme les archives.
    const dest = String(row[cDest] || '').trim();
    const zone = (/^pris/.test(status) && /^\d+$/.test(dest)) ? dest : String(row[cZone] || '').trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{1,2}:\d{2}:\d{2}$/.test(time)) continue;
    if (!/^\d+$/.test(zone)) continue;
    if (status === 'historique_xlsx') {
      if (String(row[cTreatment] || '').trim() === 'Exclu') continue;
    } else if (!/^(recu|pris)/.test(status)) continue;
    const p = time.split(':').map(Number);
    out.push({ date: date, weekday: weekdayOf_(date), slot: Math.floor((p[0] * 60 + p[1]) / 10), zone: zone });
  }
  return out.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
}

function niveau_(days, seuils) {
  let lvl = -1;
  for (let i = 0; i < seuils.length; i++) if (days >= seuils[i]) lvl = i;
  return lvl;
}

/**
 * @return {{jours: Array, semaines: Array, niveaux: Array}}
 *  jours : [date, jour, tranches jugées, prédictions, réalisées, taux, taux sans récurrence,
 *           taux Faible, Moyen, Fort, Très fort]
 *  semaines : [lundi de la semaine, prédictions, réalisées, taux, taux sans récurrence]
 *  niveaux : [niveau, prédictions, réalisées, taux] sur tout l'historique jugé
 */
function fiabilite_(values, seuils) {
  const calls = appels_(values);
  const byDate = {};
  calls.forEach(c => (byDate[c.date] = byDate[c.date] || []).push(c));
  const dates = Object.keys(byDate).sort();

  const index = {};      // weekday -> "slot|zone" -> Set(dates), journées précédentes seulement
  const zonesConnues = new Set();
  const jours = [];
  const parNiveau = LEVELS.map(() => [0, 0]);

  dates.forEach(date => {
    const dayCalls = byDate[date];
    const w = dayCalls[0].weekday;

    // Ce qui s'est passé ce jour-là, à 10 minutes près.
    const realise = new Set();
    const tranchesJugees = new Set();
    dayCalls.forEach(c => {
      for (let s = c.slot - 1; s <= c.slot + 1; s++) {
        if (s < 0 || s >= SLOTS_PER_DAY) continue;
        realise.add(s + '|' + c.zone);
        tranchesJugees.add(s);
      }
    });

    // Prédictions faites avec les journées précédentes, sur les tranches jugées.
    const idx = index[w] || {};
    let pred = 0, ok = 0;
    const niv = LEVELS.map(() => [0, 0]);
    const predites = new Set();
    Object.keys(idx).forEach(key => {
      const s = Number(key.split('|')[0]);
      if (!tranchesJugees.has(s)) return;
      const lvl = niveau_(idx[key].size, seuils);
      if (lvl < 0) return;
      predites.add(key);
      const hit = realise.has(key) ? 1 : 0;
      pred++; ok += hit;
      niv[lvl][0]++; niv[lvl][1] += hit;
      parNiveau[lvl][0]++; parNiveau[lvl][1] += hit;
    });

    // Référence : les couples tranche × zone connue sans récurrence.
    let base = 0, baseOk = 0;
    tranchesJugees.forEach(s => {
      zonesConnues.forEach(z => {
        const key = s + '|' + z;
        if (predites.has(key)) return;
        base++;
        if (realise.has(key)) baseOk++;
      });
    });

    if (pred > 0) {
      jours.push([date, WEEKDAY_NAMES[w], tranchesJugees.size, pred, ok, ok / pred,
        base ? baseOk / base : '',
        ...niv.map(n => (n[0] ? n[1] / n[0] : ''))]);
    }

    // La journée rejoint l'historique pour les jours suivants.
    const target = (index[w] = index[w] || {});
    dayCalls.forEach(c => {
      zonesConnues.add(c.zone);
      for (let s = c.slot - 1; s <= c.slot + 1; s++) {
        if (s < 0 || s >= SLOTS_PER_DAY) continue;
        const key = s + '|' + c.zone;
        (target[key] = target[key] || new Set()).add(date);
      }
    });
  });

  // Semaines (du lundi au dimanche), pondérées par le nombre de prédictions.
  const sem = {};
  jours.forEach(j => {
    const d = new Date(j[0] + 'T00:00:00Z');
    const lundi = new Date(d.getTime() - (weekdayOf_(j[0]) - 1) * 86400000).toISOString().slice(0, 10);
    const s = (sem[lundi] = sem[lundi] || [0, 0, 0, 0]);
    s[0] += j[3]; s[1] += j[4];
    if (j[6] !== '') { s[2] += j[6] * j[2]; s[3] += j[2]; }
  });
  const semaines = Object.keys(sem).sort().map(k => [k, sem[k][0], sem[k][1], sem[k][1] / sem[k][0],
    sem[k][3] ? sem[k][2] / sem[k][3] : '']);

  const niveaux = LEVELS.map((l, i) => [l, parNiveau[i][0], parNiveau[i][1],
    parNiveau[i][0] ? parNiveau[i][1] / parNiveau[i][0] : '']);

  return { jours: jours, semaines: semaines, niveaux: niveaux };
}

function ecrireFiabilite_(ss, f) {
  let sh = ss.getSheetByName(FIABILITE_SHEET);
  if (!sh) sh = ss.insertSheet(FIABILITE_SHEET);
  sh.clearContents();
  const need = Math.max(f.jours.length, f.semaines.length) + 2;
  if (sh.getMaxRows() < need) sh.insertRowsAfter(sh.getMaxRows(), need - sh.getMaxRows());
  if (sh.getMaxColumns() < 22) sh.insertColumnsAfter(sh.getMaxColumns(), 22 - sh.getMaxColumns());

  sh.getRange(1, 1, 1, 11).setValues([['Date', 'Jour', 'Tranches jugées', 'Prédictions', 'Réalisées',
    'Taux de réussite', 'Taux sans récurrence', 'Taux Faible', 'Taux Moyen', 'Taux Fort', 'Taux Très fort']]);
  if (f.jours.length) sh.getRange(2, 1, f.jours.length, 11).setValues(f.jours);

  sh.getRange(1, 13, 1, 5).setValues([['Semaine du', 'Prédictions', 'Réalisées', 'Taux de réussite', 'Taux sans récurrence']]);
  if (f.semaines.length) sh.getRange(2, 13, f.semaines.length, 5).setValues(f.semaines);

  sh.getRange(1, 19, 1, 4).setValues([['Poids', 'Prédictions', 'Réalisées', 'Taux de réussite']]);
  sh.getRange(2, 19, f.niveaux.length, 4).setValues(f.niveaux);

  sh.getRange('A2:A').setNumberFormat('yyyy-mm-dd');
  sh.getRange('M2:M').setNumberFormat('yyyy-mm-dd');
  sh.getRange('F2:K').setNumberFormat('0.0%');
  sh.getRange('P2:Q').setNumberFormat('0.0%');
  sh.getRange('V2:V').setNumberFormat('0.0%');
}

// Pour les tests hors de Google (Node).
if (typeof module !== 'undefined') {
  module.exports = { calculer_: calculer_, weekdayOf_: weekdayOf_, fiabilite_: fiabilite_ };
}
