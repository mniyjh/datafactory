import { chromium } from 'playwright';

const TARGET = 'http://10.159.243.152:3000';
const SCREENSHOT_DIR = new URL('./site-analysis/', import.meta.url);

console.log('Launching browser...');
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 1920, height: 1080 },
  locale: 'zh-CN'
});
const page = await context.newPage();

// Listen for console errors
const errors = [];
page.on('console', msg => {
  if (msg.type() === 'error') errors.push(msg.text());
});

// Listen for failed requests
const failedRequests = [];
page.on('requestfailed', req => {
  failedRequests.push({ url: req.url(), failure: req.failure()?.errorText });
});

console.log(`Navigating to ${TARGET}...`);
try {
  await page.goto(TARGET, { waitUntil: 'networkidle', timeout: 30000 });
} catch (e) {
  console.log('Navigation warning:', e.message);
}

await page.waitForTimeout(2000);

// Take homepage screenshot
const pageTitle = await page.title();
console.log(`Page title: ${pageTitle}`);

await page.screenshot({ path: './site-analysis/01-homepage.png', fullPage: true });
console.log('Screenshot 1: homepage taken');

// Analyze the page structure
const pageInfo = await page.evaluate(() => {
  const info = {
    title: document.title,
    url: window.location.href,
    // Sidebar/menu items
    menuItems: Array.from(document.querySelectorAll('.ant-menu-item, .ant-menu-submenu-title'))
      .map(el => el.textContent?.trim()).filter(Boolean),
    // Main content
    mainHeadings: Array.from(document.querySelectorAll('h1, h2, h3'))
      .map(el => el.textContent?.trim()).filter(Boolean),
    // Any visible cards or panels
    cardTitles: Array.from(document.querySelectorAll('.ant-card-head-title'))
      .map(el => el.textContent?.trim()).filter(Boolean),
    // Stats on dashboard
    statValues: Array.from(document.querySelectorAll('.stat-value'))
      .map(el => el.textContent?.trim()).filter(Boolean),
    statTitles: Array.from(document.querySelectorAll('.stat-title'))
      .map(el => el.textContent?.trim()).filter(Boolean),
    // Tables
    tables: Array.from(document.querySelectorAll('.ant-table')).length,
    // Forms/inputs
    formItems: Array.from(document.querySelectorAll('.ant-form-item-label label'))
      .map(el => el.textContent?.trim()).filter(Boolean),
  };
  return info;
});

console.log('\n=== Page Structure Analysis ===');
console.log(JSON.stringify(pageInfo, null, 2));

// Navigate to different pages to analyze
const routes = [
  { name: 'Task', path: '#/task' },
  { name: 'Database', path: '#/database' },
  { name: 'API Config', path: '#/api-config' },
  { name: 'Script', path: '#/script' },
  { name: 'Component', path: '#/component' },
  { name: 'Schedule', path: '#/schedule' },
  { name: 'Execute Log', path: '#/execute-log' },
  { name: 'Open API', path: '#/open-api' },
  { name: 'Dashboard', path: '#/dashboard' },
];

for (const route of routes) {
  console.log(`\nNavigating to ${route.name} (${route.path})...`);
  try {
    await page.goto(`${TARGET}/${route.path}`, { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(1000);
    await page.screenshot({
      path: `./site-analysis/02-${route.name.toLowerCase().replace(/\s+/g, '-')}.png`,
      fullPage: true
    });

    const roadInfo = await page.evaluate(() => ({
      title: document.title,
      headings: Array.from(document.querySelectorAll('h1, h2, h3'))
        .map(el => el.textContent?.trim()).filter(Boolean).slice(0, 5),
      tableColumns: Array.from(document.querySelectorAll('.ant-table-thead th'))
        .map(el => el.textContent?.trim()).filter(Boolean),
      buttons: Array.from(document.querySelectorAll('.ant-btn:not(.ant-btn-link)'))
        .map(el => el.textContent?.trim()).filter(Boolean).slice(0, 5),
    }));
    console.log(`  ${route.name}:`, JSON.stringify(roadInfo));
  } catch (e) {
    console.log(`  ${route.name} error:`, e.message);
  }
}

// Check API connectivity
console.log('\n=== API Connectivity ===');
try {
  const apiResp = await page.evaluate(async () => {
    try {
      const res = await fetch('/api/dashboard/overview');
      const data = await res.json();
      return { ok: res.ok, status: res.status, data };
    } catch (e) {
      return { error: e.message };
    }
  });
  console.log('Dashboard API:', JSON.stringify(apiResp));
} catch (e) {
  console.log('Dashboard API error:', e.message);
}

// Check localStorage/sessionStorage for auth
const storage = await page.evaluate(() => ({
  localStorage: Object.keys(localStorage).reduce((acc, k) => {
    acc[k] = localStorage.getItem(k); return acc;
  }, {}),
  sessionStorage: Object.keys(sessionStorage).reduce((acc, k) => {
    acc[k] = sessionStorage.getItem(k); return acc;
  }, {}),
}));
console.log('\n=== Storage ===');
console.log('localStorage:', JSON.stringify(storage.localStorage, null, 2));
console.log('sessionStorage:', JSON.stringify(storage.sessionStorage, null, 2));

if (errors.length) {
  console.log('\n=== Console Errors ===');
  errors.forEach(e => console.log(e));
}

if (failedRequests.length) {
  console.log('\n=== Failed Requests ===');
  failedRequests.forEach(f => console.log(f.url, '-', f.failure));
}

console.log('\n=== Analysis Complete ===');
console.log('Screenshots saved to ./site-analysis/');

await browser.close();
