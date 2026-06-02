import { chromium } from 'playwright';

const TARGET = 'http://10.159.243.152:3000';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });

// 1. Check page title and all visible content on each route
console.log('=== 1. PAGE CONTENT ANALYSIS ===');

const routes = [
  '#/dashboard',
  '#/task',
  '#/database',
  '#/api-config',
  '#/script',
  '#/component',
  '#/schedule',
  '#/execute-log',
  '#/open-api',
];

for (const route of routes) {
  await page.goto(`${TARGET}/${route}`, { waitUntil: 'networkidle', timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(1500);

  const info = await page.evaluate(() => {
    const getText = (sel) => Array.from(document.querySelectorAll(sel)).map(e => e.textContent.trim()).filter(Boolean);

    return {
      route: window.location.hash,
      title: document.title,
      headings: getText('.ant-card-head-title, h2, h3, .page-title').slice(0, 10),
      tableCols: getText('.ant-table-thead th').slice(0, 15),
      tableRows: document.querySelectorAll('.ant-table-tbody tr').length,
      stats: getText('.stat-title, .stat-value'),
      menu: getText('.ant-menu-item-selected'),
      breadcrumb: getText('.ant-breadcrumb'),
      badges: getText('.ant-badge'),
    };
  });
  console.log(`\n--- ${route} ---`);
  console.log(JSON.stringify(info, null, 2));
}

// 2. Check API health
console.log('\n\n=== 2. API ANALYSIS ===');

const apis = [
  '/api/dashboard/overview',
  '/api/statistics/overview',
  '/api/tasks?current=1&size=5',
  '/api/datasource/db?current=1&size=5',
  '/api/external-api?current=1&size=5',
  '/api/script?current=1&size=5',
  '/api/component?current=1&size=5',
  '/api/schedule?current=1&size=5',
  '/api/executor/log?current=1&size=5',
  '/api/open-api?current=1&size=5',
];

for (const api of apis) {
  try {
    const res = await page.evaluate(async (url) => {
      try {
        const r = await fetch(url);
        const text = await r.text();
        return { status: r.status, ok: r.ok, body: text.substring(0, 300) };
      } catch (e) {
        return { error: e.message };
      }
    }, api);
    console.log(`${api}: ${res.status} ${res.ok ? 'OK' : 'FAIL'} - ${res.body || res.error}`);
  } catch (e) {
    console.log(`${api}: ERROR - ${e.message}`);
  }
}

// 3. Check for differences from local codebase
console.log('\n\n=== 3. FEATURE DIFF vs LOCAL ===');
await page.goto(`${TARGET}/#/dashboard`, { waitUntil: 'networkidle', timeout: 15000 }).catch(() => {});
await page.waitForTimeout(1500);

const menuTree = await page.evaluate(() => {
  const items = [];
  document.querySelectorAll('.ant-menu-submenu, .ant-menu-item').forEach(el => {
    const title = el.querySelector('.ant-menu-title-content, .ant-menu-submenu-title')?.textContent?.trim();
    const key = el.getAttribute('data-menu-id') || el.getAttribute('data-key');
    const type = el.classList.contains('ant-menu-submenu') ? 'submenu' : 'item';
    if (title) items.push({ type, title, key });
  });
  return items;
});
console.log('Menu structure:', JSON.stringify(menuTree, null, 2));

// Look for 智能体助手 page
await page.goto(`${TARGET}/#/agent`, { waitUntil: 'networkidle', timeout: 10000 }).catch(() => {});
await page.waitForTimeout(1500);
await page.screenshot({ path: './site-analysis/03-agent-page.png', fullPage: true });
const agentInfo = await page.evaluate(() => ({
  hash: window.location.hash,
  title: document.title,
  body: document.body.innerText.substring(0, 500),
}));
console.log('\n智能体助手 page:', JSON.stringify(agentInfo, null, 2));

// 4. Detailed Execute Log page with modal (the thing we fixed earlier)
await page.goto(`${TARGET}/#/execute-log`, { waitUntil: 'networkidle', timeout: 15000 }).catch(() => {});
await page.waitForTimeout(2000);

// Click the first "详情" button
await page.evaluate(() => {
  const btns = Array.from(document.querySelectorAll('.ant-btn'));
  const detailBtn = btns.find(b => b.textContent.trim() === '详情');
  if (detailBtn) detailBtn.click();
});
await page.waitForTimeout(1500);
await page.screenshot({ path: './site-analysis/04-execute-detail-modal.png', fullPage: true });

// Check if the modal overflows
const modalInfo = await page.evaluate(() => {
  const modal = document.querySelector('.ant-modal');
  const body = document.querySelector('.ant-modal-body');
  const desc = document.querySelector('.ant-descriptions-view');
  if (!modal || !body) return { modalExists: false };

  return {
    modalExists: true,
    modalWidth: modal.offsetWidth,
    modalScrollWidth: modal.scrollWidth,
    bodyWidth: body.offsetWidth,
    bodyScrollWidth: body.scrollWidth,
    descWidth: desc?.offsetWidth,
    descScrollWidth: desc?.scrollWidth,
    isOverflowing: body.scrollWidth > body.offsetWidth,
  };
});
console.log('\n=== Execute Detail Modal Overflow Check ===');
console.log(JSON.stringify(modalInfo, null, 2));

// Try the 组件日志 button too
await page.keyboard.press('Escape');
await page.waitForTimeout(500);

await page.evaluate(() => {
  const btns = Array.from(document.querySelectorAll('.ant-btn'));
  const nodeLogBtn = btns.find(b => b.textContent.trim() === '组件日志');
  if (nodeLogBtn) nodeLogBtn.click();
});
await page.waitForTimeout(1500);
await page.screenshot({ path: './site-analysis/05-node-log-modal.png', fullPage: true });

const nodeModalInfo = await page.evaluate(() => {
  const modal = document.querySelector('.ant-modal');
  const body = document.querySelector('.ant-modal-body');
  if (!modal || !body) return { modalExists: false };
  return {
    modalExists: true,
    modalWidth: modal.offsetWidth,
    modalScrollWidth: modal.scrollWidth,
    bodyWidth: body.offsetWidth,
    bodyScrollWidth: body.scrollWidth,
    isOverflowing: body.scrollWidth > body.offsetWidth,
  };
});
console.log('\n=== Node Log Modal Overflow Check ===');
console.log(JSON.stringify(nodeModalInfo, null, 2));

await browser.close();
console.log('\nDone!');
