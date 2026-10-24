'use strict';
// VISIOO admin. Plain JS, no dependencies. Everything from the server is inserted with textContent, never as HTML.
const API = '/api/v1';
const app = document.getElementById('app');
const store = {
  get token() { try { return sessionStorage.getItem('visioo_admin_token'); } catch { return null; } },
  set token(v) { try { v ? sessionStorage.setItem('visioo_admin_token', v) : sessionStorage.removeItem('visioo_admin_token'); } catch { /* private mode */ } },
  get role() { try { return sessionStorage.getItem('visioo_admin_role'); } catch { return null; } },
  set role(v) { try { v ? sessionStorage.setItem('visioo_admin_role', v) : sessionStorage.removeItem('visioo_admin_role'); } catch { /* private mode */ } },
};

const ERRORS = {
  INVALID_CREDENTIALS: 'Email ou mot de passe incorrect.',
  RATE_LIMITED: 'Trop de tentatives. Réessayez dans une minute.',
  FORBIDDEN: 'Votre rôle ne permet pas cette action.',
  UNAUTHORIZED: 'Session expirée. Reconnectez-vous.',
  PLAN_NOT_FOUND: 'Formule introuvable.',
  VALIDATION_ERROR: 'Valeurs invalides. Vérifiez le formulaire.',
  KEY_NOT_FOUND: 'Clé introuvable.',
  DEVICE_NOT_FOUND: 'Appareil introuvable.',
};

async function call(path, { method = 'GET', body } = {}) {
  let res;
  try {
    res = await fetch(API + path, {
      method,
      // Only declare JSON when there is a body: the server rejects an empty body labelled as JSON.
      headers: { ...(body ? { 'content-type': 'application/json' } : {}), ...(store.token ? { authorization: 'Bearer ' + store.token } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new Error('Connexion impossible au serveur.');
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 401 && store.token) { store.token = null; store.role = null; render(); }
    throw new Error(ERRORS[data?.error?.code] || 'Une erreur est survenue.');
  }
  return data;
}

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else if (v !== false && v != null) node.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) if (c != null) node.append(c.nodeType ? c : document.createTextNode(String(c)));
  return node;
}
const msg = (kind, text) => el('div', { class: 'msg ' + kind, role: kind === 'err' ? 'alert' : 'status' }, text);
const fmtDate = (s) => (s ? new Date(s).toLocaleDateString('fr-FR') : '—');
const field = (label, input) => el('div', {}, el('label', {}, label), input);

function render() {
  app.replaceChildren(store.token ? shell() : loginView());
}

function loginView() {
  const out = el('div');
  const email = el('input', { type: 'email', autocomplete: 'username', required: true });
  const password = el('input', { type: 'password', autocomplete: 'current-password', required: true });
  const submit = el('button', { type: 'submit' }, 'Se connecter');
  const form = el('form', { class: 'card login', onsubmit: async (e) => {
    e.preventDefault();
    submit.disabled = true;
    out.replaceChildren();
    try {
      const r = await call('/admin/auth/login', { method: 'POST', body: { email: email.value, password: password.value } });
      store.token = r.accessToken;
      store.role = r.role;
      render();
    } catch (err) {
      out.replaceChildren(msg('err', err.message));
      submit.disabled = false;
    }
  } },
    el('h1', {}, el('b', {}, 'V'), 'ISIOO ', 'Admin'),
    field('Email', email), field('Mot de passe', password), out,
    el('div', { class: 'actions' }, submit));
  return form;
}

function shell() {
  const content = el('div');
  const tabs = { keys: ['Clés', keysView], generate: ['Générer des clés', generateView], users: ['Utilisateurs', usersView] };
  const nav = el('nav');
  const show = (name) => {
    for (const b of nav.children) b.classList.toggle('on', b.dataset.tab === name), b.classList.toggle('sec', b.dataset.tab !== name);
    content.replaceChildren(tabs[name][1]());
  };
  for (const [name, [label]] of Object.entries(tabs)) nav.append(el('button', { 'data-tab': name, class: 'sec', onclick: () => show(name) }, label));
  const root = el('div', {},
    el('header', {}, el('h1', {}, el('b', {}, 'V'), 'ISIOO ', 'Admin'),
      el('div', {}, el('span', { class: 'muted' }, 'Rôle : ' + (store.role || '?') + ' '),
        el('button', { class: 'sec', onclick: () => { store.token = null; store.role = null; render(); } }, 'Déconnexion'))),
    nav, content);
  show('keys');
  return root;
}

let plansCache = null;
async function plans() {
  plansCache ??= (await call('/admin/vip/plans')).plans;
  return plansCache;
}

function generateView() {
  const box = el('div');
  const result = el('div');
  const plan = el('select');
  const quantity = el('input', { type: 'number', min: 1, max: 1000, value: 10, required: true });
  const devices = el('input', { type: 'number', min: 1, max: 10, placeholder: 'selon la formule' });
  const expires = el('input', { type: 'date' });
  const notes = el('input', { type: 'text', maxlength: 500, placeholder: 'ex. lot revendeur du 12 mars' });
  plans().then((list) => { for (const p of list) plan.append(el('option', { value: p.id }, `${p.name} (${p.durationDays} jours, ${p.deviceLimit} appareils)`)); })
    .catch((e) => box.prepend(msg('err', e.message)));
  const submit = el('button', { type: 'submit' }, 'Générer');
  const form = el('form', { class: 'card', onsubmit: async (e) => {
    e.preventDefault();
    submit.disabled = true;
    result.replaceChildren();
    try {
      const body = { planId: plan.value, quantity: Number(quantity.value) };
      if (devices.value) body.deviceLimit = Number(devices.value);
      if (expires.value) body.expiresAt = new Date(expires.value + 'T23:59:59').toISOString();
      if (notes.value.trim()) body.notes = notes.value.trim();
      const r = await call('/admin/vip/keys', { method: 'POST', body });
      result.replaceChildren(keysResult(r.keys, plan.selectedOptions[0].textContent, body.deviceLimit));
    } catch (err) {
      result.replaceChildren(msg('err', err.message));
    } finally { submit.disabled = false; }
  } },
    el('h2', {}, 'Générer des clés VIP'),
    el('div', { class: 'row' }, field('Formule', plan), field('Quantité', quantity), field('Appareils par clé', devices), field('À activer avant (facultatif)', expires)),
    field('Note interne (facultatif)', notes),
    el('p', { class: 'muted' }, 'La durée de l’abonnement commence quand l’utilisateur active la clé.'),
    el('div', { class: 'actions' }, submit));
  box.append(form, result);
  return box;
}

function keysResult(keys, planLabel, deviceLimit) {
  const text = keys.join('\n');
  const area = el('textarea', { readonly: true, 'aria-label': 'Clés générées' }); area.value = text;
  const copied = el('span', { class: 'muted' });
  const csv = () => {
    const created = new Date().toISOString();
    const lines = ['key,plan,device_limit,status,created_at', ...keys.map((k) => `${k},${planLabel.replace(/,/g, ' ')},${deviceLimit ?? ''},active,${created}`)];
    const url = URL.createObjectURL(new Blob([lines.join('\n') + '\n'], { type: 'text/csv' }));
    const a = el('a', { href: url, download: `visioo-cles-${created.slice(0, 10)}.csv` });
    document.body.append(a); a.click(); a.remove(); URL.revokeObjectURL(url);
  };
  return el('div', { class: 'card' },
    msg('warn', `${keys.length} clé(s) générée(s). Notez-les maintenant : elles ne pourront plus être affichées ensuite.`),
    area,
    el('div', { class: 'actions' },
      el('button', { onclick: async () => { try { await navigator.clipboard.writeText(text); copied.textContent = 'Copié.'; } catch { area.select(); copied.textContent = 'Sélectionné : copiez avec Ctrl+C.'; } } }, 'Copier'),
      el('button', { class: 'sec', onclick: csv }, 'Télécharger le CSV'), copied));
}

function keysView() {
  const box = el('div');
  const status = el('select', { onchange: () => load(true) },
    el('option', { value: '' }, 'Tous les statuts'), el('option', { value: 'active' }, 'Disponibles'),
    el('option', { value: 'used' }, 'Utilisées'), el('option', { value: 'revoked' }, 'Annulées'));
  const tbody = el('tbody');
  const feedback = el('div');
  const more = el('button', { class: 'sec', onclick: () => load(false) }, 'Voir plus');
  let offset = 0;
  const LIMIT = 50;
  let planNames = {};
  async function load(reset) {
    if (reset) { offset = 0; tbody.replaceChildren(); }
    try {
      planNames = Object.fromEntries((await plans()).map((p) => [p.id, p.name]));
      const q = `?limit=${LIMIT}&offset=${offset}` + (status.value ? `&status=${status.value}` : '');
      const { keys } = await call('/admin/vip/keys' + q);
      offset += keys.length;
      more.hidden = keys.length < LIMIT;
      feedback.replaceChildren(keys.length === 0 && offset === 0 ? msg('ok', 'Aucune clé pour ce filtre.') : '');
      for (const k of keys) tbody.append(row(k));
    } catch (e) { feedback.replaceChildren(msg('err', e.message)); }
  }
  function row(k) {
    const cancel = el('button', { class: 'danger', disabled: k.status === 'revoked', onclick: async () => {
      if (!confirm(`Annuler la clé ${k.prefix}… ? L’accès VIP qu’elle a donné sera retiré.`)) return;
      try { await call(`/admin/vip/keys/${k.id}/revoke`, { method: 'POST' }); load(true); } catch (e) { feedback.replaceChildren(msg('err', e.message)); }
    } }, 'Annuler');
    return el('tr', {}, el('td', {}, k.prefix + '-…'), el('td', {}, planNames[k.planId] || k.planId),
      el('td', {}, el('span', { class: 'pill ' + k.status }, { active: 'Disponible', used: 'Utilisée', revoked: 'Annulée' }[k.status] || k.status)),
      el('td', {}, `${k.activationCount}/${k.maxActivations}`), el('td', {}, fmtDate(k.createdAt)),
      el('td', {}, fmtDate(k.expiresAt)), el('td', {}, k.notes || ''), el('td', {}, cancel));
  }
  box.append(el('div', { class: 'card' }, el('h2', {}, 'Clés'), status, feedback,
    el('div', { class: 'tablewrap' }, el('table', {}, el('thead', {}, el('tr', {}, ...['Début de la clé', 'Formule', 'Statut', 'Activations', 'Créée', 'À activer avant', 'Note', ''].map((h) => el('th', {}, h)))), tbody)),
    el('div', { class: 'actions' }, more),
    el('p', { class: 'muted' }, 'Seul le début de chaque clé est conservé. La clé complète n’est visible qu’au moment de la génération.')));
  load(true);
  return box;
}

function usersView() {
  const box = el('div');
  const search = el('input', { type: 'search', placeholder: 'Rechercher par email' });
  const tbody = el('tbody');
  const feedback = el('div');
  const detail = el('div');
  let timer;
  async function load() {
    try {
      const { users } = await call('/admin/users?limit=50' + (search.value.trim() ? '&q=' + encodeURIComponent(search.value.trim()) : ''));
      feedback.replaceChildren(users.length === 0 ? msg('ok', 'Aucun utilisateur.') : '');
      tbody.replaceChildren(...users.map((u) => el('tr', {}, el('td', {}, u.email), el('td', {}, u.vip ? el('span', { class: 'pill active' }, 'VIP') : '—'),
        el('td', {}, String(u.devices)), el('td', {}, fmtDate(u.createdAt)), el('td', {}, el('button', { class: 'sec', onclick: () => devicesOf(u) }, 'Appareils')))));
    } catch (e) { feedback.replaceChildren(msg('err', e.message)); }
  }
  async function devicesOf(u) {
    try {
      const { devices } = await call(`/admin/users/${u.id}/devices`);
      const rows = devices.map((d) => el('tr', {}, el('td', {}, [d.platform, d.model].filter(Boolean).join(' · ')), el('td', {}, fmtDate(d.lastSeenAt)),
        el('td', {}, d.revokedAt ? el('span', { class: 'pill revoked' }, 'Retiré') : el('button', { class: 'danger', onclick: async () => {
          if (!confirm('Retirer cet appareil ? Il sera déconnecté immédiatement.')) return;
          try { await call(`/admin/devices/${d.id}`, { method: 'DELETE' }); devicesOf(u); load(); } catch (e) { detail.prepend(msg('err', e.message)); }
        } }, 'Retirer'))));
      detail.replaceChildren(el('div', { class: 'card' }, el('h2', {}, 'Appareils de ' + u.email),
        el('div', { class: 'tablewrap' }, el('table', {}, el('thead', {}, el('tr', {}, ...['Appareil', 'Dernière activité', ''].map((h) => el('th', {}, h)))), el('tbody', {}, rows)))));
    } catch (e) { detail.replaceChildren(msg('err', e.message)); }
  }
  search.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(load, 300); });
  box.append(el('div', { class: 'card' }, el('h2', {}, 'Utilisateurs'), search, feedback,
    el('div', { class: 'tablewrap' }, el('table', {}, el('thead', {}, el('tr', {}, ...['Email', 'VIP', 'Appareils', 'Inscrit le', ''].map((h) => el('th', {}, h)))), tbody))), detail);
  load();
  return box;
}

render();
