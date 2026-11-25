/* 设计稿公共脚本：注入图标 sprite + 后台框架（离线可用，不依赖 CDN） */
window.Mock = (function () {
  var ICONS =
    '<svg xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden">' +
    '<symbol id="i-dash" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></symbol>' +
    '<symbol id="i-request" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/></symbol>' +
    '<symbol id="i-inquiry" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/><path d="M8.5 11h5M11 8.5v5"/></symbol>' +
    '<symbol id="i-quote" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h16v14H4z"/><path d="M8 9h8M8 13h5M8 17h3"/></symbol>' +
    '<symbol id="i-compare" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v18"/><path d="M6 7H3l3 7H3"/><path d="M18 7h3l-3 7h3"/><path d="M6 7 3 14h6zM18 7l-3 7h6z"/></symbol>' +
    '<symbol id="i-order" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M6 6h15l-1.5 9h-12z"/><circle cx="9" cy="19" r="1.6"/><circle cx="18" cy="19" r="1.6"/><path d="M6 6 5 3H3"/></symbol>' +
    '<symbol id="i-receipt" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2z"/><path d="M9 8h6M9 12h6M9 16h3"/></symbol>' +
    '<symbol id="i-material" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9z"/><path d="m4 7.5 8 4.5 8-4.5M12 12v9"/></symbol>' +
    '<symbol id="i-supplier" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21V9l6-4 6 4v12"/><path d="M9 21v-6h3v6"/><path d="M15 13h6v8h-6"/></symbol>' +
    '<symbol id="i-rule" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16M4 12h16M4 17h16"/><circle cx="9" cy="7" r="2" fill="#fff"/><circle cx="15" cy="12" r="2" fill="#fff"/><circle cx="8" cy="17" r="2" fill="#fff"/></symbol>' +
    '<symbol id="i-user" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="3.6"/><path d="M4.5 20a7.5 7.5 0 0 1 15 0"/></symbol>' +
    '<symbol id="i-role" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="3.2"/><path d="M3 20a6 6 0 0 1 12 0"/><path d="M17 11a3 3 0 1 0 0-6"/><path d="M21 20a5 5 0 0 0-4-4.9"/></symbol>' +
    '<symbol id="i-menu" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 6h16M4 12h16M4 18h10"/></symbol>' +
    '<symbol id="i-bell" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M18 9a6 6 0 1 0-12 0c0 6-2 7-2 7h16s-2-1-2-7"/><path d="M10.5 20a2 2 0 0 0 3 0"/></symbol>' +
    '<symbol id="i-help" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.4 2.3c-.6.3-.9.8-.9 1.4v.3"/><path d="M12 17h.01"/></symbol>' +
    '<symbol id="i-approve" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12.5 9 17.5 20 6.5"/></symbol>' +
    '<symbol id="i-log" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7.5V12l3 2"/></symbol>' +
    '<symbol id="i-msg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 5h16v11H9l-5 4z"/><path d="M8 9h8M8 12.5h5"/></symbol>' +
    '<symbol id="i-search" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/></symbol>' +
    '<symbol id="i-export" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v11"/><path d="m8 11 4 4 4-4"/><path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></symbol>' +
    '<symbol id="i-plus" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></symbol>' +
    '<symbol id="i-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m9 6 6 6-6 6"/></symbol>' +
    '</svg>';

  var ROLES = {
    requester: { name: '林晓', role: '需求申请人', dept: '生产制造部', color: '#174A5C', initial: '林' },
    dept: { name: '周敏', role: '部门负责人', dept: '生产制造部', color: '#2A7F86', initial: '周' },
    buyer: { name: '陈立', role: '采购专员', dept: '采购中心', color: '#316B74', initial: '陈' },
    approver: { name: '吴总', role: '审批人', dept: '财务总监', color: '#A9761F', initial: '吴' },
    warehouse: { name: '赵磊', role: '仓管/验收', dept: '仓储物流部', color: '#247A58', initial: '赵' }
  };

  var MENUS = {
    buyer: [
      { g: '工作台' },
      { k: 'dashboard', n: '采购分析看板', i: 'i-dash' },
      { g: '需求与询价' },
      { k: 'requests', n: '采购需求池', i: 'i-request' },
      { k: 'inquiry', n: '询价比价单', i: 'i-inquiry' },
      { k: 'quote', n: '供应商报价台', i: 'i-quote' },
      { g: '决策与执行' },
      { k: 'comparison', n: '综合比价矩阵', i: 'i-compare' },
      { k: 'rules', n: '比价规则配置', i: 'i-rule' },
      { k: 'orders', n: '采购单管理', i: 'i-order' },
      { k: 'receipt', n: '到货验收', i: 'i-receipt' },
      { g: '基础数据' },
      { k: 'materials', n: '物料档案', i: 'i-material' },
      { k: 'suppliers', n: '供应商档案', i: 'i-supplier' },
      { g: '系统' },
      { k: 'users', n: '用户管理', i: 'i-user' },
      { k: 'roles', n: '角色管理', i: 'i-role' },
      { k: 'menus', n: '菜单管理', i: 'i-menu' }
    ],
    dept: [
      { g: '工作台' },
      { k: 'dashboard', n: '部门采购概览', i: 'i-dash' },
      { g: '需求管理' },
      { k: 'requests', n: '本部门需求', i: 'i-request' },
      { k: 'approve', n: '需求审批', i: 'i-approve', badge: 4 },
      { g: '跟踪' },
      { k: 'orders', n: '采购执行跟踪', i: 'i-order' },
      { k: 'msg', n: '消息中心', i: 'i-msg' }
    ],
    approver: [
      { g: '工作台' },
      { k: 'dashboard', n: '审批概览', i: 'i-dash' },
      { g: '审批' },
      { k: 'approve', n: '审批中心', i: 'i-approve', badge: 6 },
      { k: 'comparison', n: '定标结果复核', i: 'i-compare' },
      { k: 'orders', n: '采购单审批', i: 'i-order' },
      { g: '其他' },
      { k: 'msg', n: '消息中心', i: 'i-msg' }
    ],
    warehouse: [
      { g: '工作台' },
      { k: 'dashboard', n: '验收概览', i: 'i-dash' },
      { g: '到货验收' },
      { k: 'receipt', n: '待到货明细', i: 'i-receipt', badge: 5 },
      { k: 'receipts', n: '验收记录', i: 'i-receipt' },
      { k: 'returns', n: '退货登记', i: 'i-order' },
      { g: '其他' },
      { k: 'msg', n: '消息中心', i: 'i-msg' }
    ]
  };

  function shell(role, active, crumbText) {
    document.body.insertAdjacentHTML('afterbegin', ICONS);
    var u = ROLES[role] || ROLES.buyer;
    var menus = MENUS[role] || MENUS.buyer;
    var sb = document.getElementById('sb');
    if (sb) {
      sb.innerHTML =
        '<div class="brand"><span class="logo">采</span><b>询比价管理平台</b></div>' +
        '<nav class="menu">' +
        menus.map(function (m) {
          if (m.g) return '<div class="grp">' + m.g + '</div>';
          return '<a class="' + (m.k === active ? 'on' : '') + '" href="' + (m.k === 'dashboard' ? 'dashboard.html' : m.k + '.html') + '">' +
            '<svg class="ic"><use href="#' + m.i + '"/></svg>' + m.n +
            (m.badge ? '<span class="badge">' + m.badge + '</span>' : '') + '</a>';
        }).join('') +
        '</nav>' +
        '<div class="side-foot">通道 lane1 · v1.0.0</div>';
    }
    var tb = document.getElementById('tb');
    if (tb) {
      tb.innerHTML =
        '<span class="crumb">' + crumbText + '</span><span class="spacer"></span>' +
        '<span class="icon-btn" title="搜索"><svg width="17" height="17"><use href="#i-search"/></svg></span>' +
        '<span class="icon-btn" title="消息"><svg width="17" height="17"><use href="#i-bell"/></svg><i class="dot"></i></span>' +
        '<span class="icon-btn" title="帮助"><svg width="17" height="17"><use href="#i-help"/></svg></span>' +
        '<div class="who"><span style="width:30px;height:30px;border-radius:50%;background:' + u.color + ';color:#fff;display:flex;align-items:center;justify-content:center;font-size:12px">' + u.initial + '</span>' +
        '<div><div class="n">' + u.name + '</div><div class="r">' + u.role + '</div></div></div>';
    }
  }

  return { shell: shell, roles: ROLES };
})();
