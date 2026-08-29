/**
 * F-11.9 展示层格式化逻辑，从组件里拆出来是为了能被 web/test/unit 直接单测，
 * 不依赖渲染框架。阈值判断本身以后端 overThreshold 字段为准，本函数只负责百分比格式化，
 * 避免前后端各算一遍、口径不一致。
 */
export function formatRatioAsPercent(ratio: number, fractionDigits = 3): string {
  return `${(ratio * 100).toFixed(fractionDigits)}%`;
}

export function formatRatioAsPerMille(ratio: number, fractionDigits = 2): string {
  return `${(ratio * 1000).toFixed(fractionDigits)}‰`;
}
