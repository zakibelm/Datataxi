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

/** Journées minimum pour afficher une récurrence (lu dans le tableau de bord, case B26). */
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
}

function lireMinDays_(ss) {
  const dash = ss.getSheetByName(DASHBOARD_SHEET);
  const v = dash ? Number(dash.getRange('B26').getValue()) : NaN;
  return v >= 1 ? v : DEFAULT_MIN_DAYS;
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
  const cStatus = col('current_status'), cTreatment = col('source_treatment');

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
    const zone = String(row[cZone] || '').trim();
    const status = String(row[cStatus] || '').trim();
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

// Pour les tests hors de Google (Node).
if (typeof module !== 'undefined') module.exports = { calculer_: calculer_, weekdayOf_: weekdayOf_ };
