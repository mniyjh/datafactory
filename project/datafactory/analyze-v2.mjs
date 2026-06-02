import { chromium } from 'playwright';

const TARGET = 'http://10.159.243.152:3000';

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });

// Track all network requests
const apiCalls = [];
page.on('response', async (resp) => {
  const url = resp.url();
  if (url.includes('/api/')) {
    let body = '';
    try { body = await resp.text(); } catch (_) {}
    apiCalls.push({
      url: url.replace(TARGET, '').replace('http://10.159.243.152:8080', ''),
      status: resp.status(),
      ok: resp.ok(),
      body: body.substring(0, 200),
    });
  }
});

console.log('=== 数据工厂网站分析 v2 ===\n');

// 1. Visit dashboard
console.log('1. 访问首页 (Dashboard)...');
await page.goto(TARGET, { waitUntil: 'networkidle', timeout: 30000 });
await page.waitForTimeout(2000);

// Check page state
let info = await page.evaluate(() => ({
  title: document.title,
  hash: window.location.hash,
  menu: Array.from(document.querySelectorAll('.ant-menu-item, .ant-menu-submenu-title'))
    .map(el => el.textContent?.trim()).filter(Boolean),
  statValues: Array.from(document.querySelectorAll('.stat-value'))
    .map(el => el.textContent?.trim()),
  statTitles: Array.from(document.querySelectorAll('.stat-title'))
    .map(el => el.textContent?.trim()),
  cardTitles: Array.from(document.querySelectorAll('.ant-card-head-title'))
    .map(el => el.textContent?.trim()),
  emptyText: Array.from(document.querySelectorAll('.ant-empty-description'))
    .map(el => el.textContent?.trim()),
  errorMsg: Array.from(document.querySelectorAll('.ant-result-title'))
    .map(el => el.textContent?.trim()),
}));

console.log('标题:', info.title);
console.log('当前路由:', info.hash);
console.log('统计值:', info.statValues);
console.log('统计标题:', info.statTitles);
console.log('卡片:', info.cardTitles);
console.log('空状态:', info.emptyText);
console.log('错误信息:', info.errorMsg);
await page.screenshot({ path: './site-analysis/v2-01-dashboard.png', fullPage: true });

// 2. Visit task management
console.log('\n2. 访问任务管理...');
await page.goto(`${TARGET}/#/task`, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(2000);
info = await page.evaluate(() => ({
  title: document.title,
  tableCols: Array.from(document.querySelectorAll('.ant-table-thead th'))
    .map(el => el.textContent?.trim()),
  tableRows: document.querySelectorAll('.ant-table-tbody tr').length,
  buttons: Array.from(document.querySelectorAll('.ant-btn:not(.ant-btn-link)'))
    .map(el => el.textContent?.trim()).slice(0, 10),
  emptyText: Array.from(document.querySelectorAll('.ant-empty-description'))
    .map(el => el.textContent?.trim()),
}));
console.log(JSON.stringify(info, null, 2));
await page.screenshot({ path: './site-analysis/v2-02-task.png', fullPage: true });

// 3. Visit database management
console.log('\n3. 访问数据库管理...');
await page.goto(`${TARGET}/#/database`, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(2000);
info = await page.evaluate(() => ({
  tableCols: Array.from(document.querySelectorAll('.ant-table-thead th')).map(el => el.textContent?.trim()),
  tableRows: document.querySelectorAll('.ant-table-tbody tr').length,
  buttons: Array.from(document.querySelectorAll('.ant-btn:not(.ant-btn-link)')).map(el => el.textContent?.trim()).slice(0, 8),
  emptyText: Array.from(document.querySelectorAll('.ant-empty-description')).map(el => el.textContent?.trim()),
}));
console.log(JSON.stringify(info, null, 2));
await page.screenshot({ path: './site-analysis/v2-03-database.png', fullPage: true });

// 4. Visit API config
console.log('\n4. 访问三方API管理...');
await page.goto(`${TARGET}/#/api-config`, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(1500);
info = await page.evaluate(() => ({
  tableCols: Array.from(document.querySelectorAll('.ant-table-thead th')).map(el => el.textContent?.trim()),
  tableRows: document.querySelectorAll('.ant-table-tbody tr').length,
  buttons: Array.from(document.querySelectorAll('.ant-btn:not(.ant-btn-link)')).map(el => el.textContent?.trim()).slice(0, 8),
}));
console.log(JSON.stringify(info, null, 2));
await page.screenshot({ path: './site-analysis/v2-04-api-config.png', fullPage: true });

// 5. Visit script management
console.log('\n5. 访问脚本管理...');
await page.goto(`${TARGET}/#/script`, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(1500);
info = await page.evaluate(() => ({
  tableCols: Array.from(document.querySelectorAll('.ant-table-thead th')).map(el => el.textContent?.trim()),
  tableRows: document.querySelectorAll('.ant-table-tbody tr').length,
  buttons: Array.from(document.querySelectorAll('.ant-btn:not(.ant-btn-link)')).map(el => el.textContent?.trim()).slice(0, 8),
}));
console.log(JSON.stringify(info, null, 2));
await page.screenshot({ path: './site-analysis/v2-05-script.png', fullPage: true });

// 6. Visit component management
console.log('\n6. 访问组件管理...');
await page.goto(`${TARGET}/#/component`, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(1500);
info = await page.evaluate(() => ({
  tableCols: Array.from(document.querySelectorAll('.ant-table-thead th')).map(el => el.textContent?.trim()),
  tableRows: document.querySelectorAll('.ant-table-tbody tr').length,
  buttons: Array.from(document.querySelectorAll('.ant-btn:not(.ant-btn-link)')).map(el => el.textContent?.trim()).slice(0, 8),
}));
console.log(JSON.stringify(info, null, 2));
await page.screenshot({ path: './site-analysis/v2-06-component.png', fullPage: true });

// 7. Visit execution log
console.log('\n7. 访问执行日志...');
await page.goto(`${TARGET}/#/execute-log`, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(2000);
info = await page.evaluate(() => ({
  tableCols: Array.from(document.querySelectorAll('.ant-table-thead th')).map(el => el.textContent?.trim()),
  tableRows: document.querySelectorAll('.ant-table-tbody tr').length,
  statValues: Array.from(document.querySelectorAll('.stat-value')).map(el => el.textContent?.trim()),
  statTitles: Array.from(document.querySelectorAll('.stat-title')).map(el => el.textContent?.trim()),
  buttons: Array.from(document.querySelectorAll('.ant-btn:not(.ant-btn-link)')).map(el => el.textContent?.trim()).slice(0, 10),
}));
console.log(JSON.stringify(info, null, 2));
await page.screenshot({ path: './site-analysis/v2-07-execute-log.png', fullPage: true });

// 8. Visit schedule
console.log('\n8. 访问调度任务...');
await page.goto(`${TARGET}/#/schedule`, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(1500);
info = await page.evaluate(() => ({
  tableCols: Array.from(document.querySelectorAll('.ant-table-thead th')).map(el => el.textContent?.trim()),
  tableRows: document.querySelectorAll('.ant-table-tbody tr').length,
  buttons: Array.from(document.querySelectorAll('.ant-btn:not(.ant-btn-link)')).map(el => el.textContent?.trim()).slice(0, 8),
  emptyText: Array.from(document.querySelectorAll('.ant-empty-description')).map(el => el.textContent?.trim()),
}));
console.log(JSON.stringify(info, null, 2));
await page.screenshot({ path: './site-analysis/v2-08-schedule.png', fullPage: true });

// 9. Visit open API
console.log('\n9. 访问开放接口...');
await page.goto(`${TARGET}/#/open-api`, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(1500);
info = await page.evaluate(() => ({
  tableCols: Array.from(document.querySelectorAll('.ant-table-thead th')).map(el => el.textContent?.trim()),
  tableRows: document.querySelectorAll('.ant-table-tbody tr').length,
  buttons: Array.from(document.querySelectorAll('.ant-btn:not(.ant-btn-link)')).map(el => el.textContent?.trim()).slice(0, 8),
}));
console.log(JSON.stringify(info, null, 2));
await page.screenshot({ path: './site-analysis/v2-09-open-api.png', fullPage: true });

// 10. Visit 智能体助手
console.log('\n10. 访问智能体助手...');
await page.goto(`${TARGET}/#/agent`, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(2000);
info = await page.evaluate(() => ({
  title: document.title,
  headings: Array.from(document.querySelectorAll('h1, h2, h3, h4')).map(el => el.textContent?.trim()),
  mainContent: document.body.innerText.substring(0, 1000),
}));
console.log('Headings:', info.headings);
console.log('Main content (first 1000 chars):');
console.log(info.mainContent);
await page.screenshot({ path: './site-analysis/v2-10-agent.png', fullPage: true });

// API Call Summary
console.log('\n\n=== API 请求汇总 (实际页面触发的请求) ===');
const uniqueApis = [...new Map(apiCalls.map(a => [a.url, a])).values()];
uniqueApis.forEach(a => {
  console.log(`[${a.status}] ${a.url} => ${a.body}`);
});

// Check frontend console errors
const consoleErrors = [];
page.on('console', msg => {
  if (msg.type() === 'error') consoleErrors.push(msg.text());
});

console.log('\n=== 前端控制台错误 ===');
if (consoleErrors.length === 0) console.log('(无控制台错误)');
consoleErrors.forEach(e => console.log(e));

await browser.close();
console.log('\n分析完成！截图保存在 site-analysis/v2-*.png');
