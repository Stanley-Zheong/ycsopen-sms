/**
 * F-3.2/F-3.3/F-3.5/F-3.6 签名与模板审核、通道报备、免审规则——尚未实现为独立 Service。
 * 当前 MessageSubmitService 里只做了"发送时校验已审核"这一半（F-3.7），
 * 审核操作本身（运营点通过/驳回）还没有 Service+Controller。见 core/docs/ROADMAP.md。
 */
package com.ycsopen.sms.core.service.signature;
