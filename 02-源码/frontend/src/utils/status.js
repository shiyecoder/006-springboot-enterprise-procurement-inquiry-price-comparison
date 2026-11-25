/** 状态字典：中文标签 + Element tag 类型。状态不能只靠颜色表达，必须同时有中文。 */
export const REQUEST_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  DEPT_PENDING: { label: '待部门审批', type: 'warning' },
  REJECTED: { label: '已退回', type: 'danger' },
  PURCHASING: { label: '待采购', type: 'primary' },
  QUOTED: { label: '已询价', type: 'success' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

export const INQUIRY_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  QUOTING: { label: '报价中', type: 'warning' },
  COMPARING: { label: '待比价', type: 'primary' },
  APPROVING: { label: '待审批', type: 'warning' },
  AWARDED: { label: '已定标', type: 'success' },
  VOID: { label: '已作废', type: 'danger' }
}

export const ORDER_STATUS = {
  PENDING: { label: '待下单', type: 'info' },
  EXECUTING: { label: '执行中', type: 'primary' },
  PARTIAL: { label: '部分到货', type: 'primary' },
  PENDING_CLOSE: { label: '待结单', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' }
}

export const URGENCY = {
  URGENT: { label: '加急', type: 'danger' },
  NORMAL: { label: '普通', type: 'warning' },
  LOW: { label: '不急', type: 'info' }
}

export const CONCLUSION = {
  QUALIFIED: { label: '合格', type: 'success' },
  PARTIAL: { label: '部分合格', type: 'warning' },
  UNQUALIFIED: { label: '不合格', type: 'danger' }
}

export const RETURN_STATUS = {
  WAIT_SUPPLIER: { label: '待供应商确认', type: 'warning' },
  EXCHANGING: { label: '换货中', type: 'primary' },
  CLOSED: { label: '已关闭', type: 'success' }
}

export const SUPPLIER_STATUS = {
  QUALIFIED: { label: '合格供方', type: 'success' },
  OBSERVE: { label: '观察期', type: 'warning' },
  DISABLED: { label: '已停用', type: 'danger' }
}

export const GRADE = {
  推荐: 'success',
  可选: 'primary',
  不推荐: 'danger'
}

export function statusOf(map, key) {
  return map[key] || { label: key || '-', type: 'info' }
}
