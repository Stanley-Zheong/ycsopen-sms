/**
 * F-4.2/F-4.3/F-4.4/F-4.6 通道热更新、健康检查、删除迁移、通道池——尚未实现。
 * 当前通道的新增/查询/暂停/恢复直接在 web.controller.ChannelController 里用
 * ChannelRepository 完成，规模够小时没有单独抽 Service 层；这几项一旦实现（尤其健康检查
 * 需要定时任务+连接探测），应该在这里新建 ChannelHealthCheckService 等类，
 * 而不是继续堆在 Controller 里。见 core/docs/ROADMAP.md。
 */
package com.ycsopen.sms.core.service.channel;
