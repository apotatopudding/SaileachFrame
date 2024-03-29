package top.angelinaBot.util;

import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.utils.BotConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.angelinaBot.Exception.AngelinaException;
import top.angelinaBot.container.AngelinaEventSource;
import top.angelinaBot.controller.*;
import top.angelinaBot.dao.ActivityMapper;
import top.angelinaBot.model.EventEnum;
import top.angelinaBot.model.MessageInfo;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Mirai框架的通用启动方法
 */
@Component
@Slf4j
public class MiraiFrameUtil {

    @Autowired
    private GroupChatController groupChatController;

    @Autowired
    private FriendChatController friendChatController;

    @Autowired
    private StrangerChatController strangerChatController;

    @Autowired
    private GroupTempChatController groupTempChatController;

    @Autowired
    private EventsController eventsController;

    @Autowired
    private ActivityMapper activityMapper;

    /**
     * rebuild状态，1为正在rebuild，0为已经rebuild完成
     */
    private volatile int status = 0;

    //qq列表
    @Value("#{'${userConfig.qqList}'.split(' ')}")
    private String[] qqList;

    //密码列表
    @Value("#{'${userConfig.pwList}'.split(' ')}")
    private String[] pwList;

    //bot的呼叫关键字
    @Value("#{'${userConfig.botNames}'.split(' ')}")
    public String[] botNames;

    @Value("#{'${userConfig.typeList}'}")
    private String typeList;

    //维护一个群号/账号服务映射，保证一个群只有一个账号提供服务
    public static final Map<Long, Long> messageIdMap = new HashMap<>();

    public final Map<String, BotConfiguration.MiraiProtocol> type = new HashMap<>();
    {
        type.put("IPAD", BotConfiguration.MiraiProtocol.IPAD);
        type.put("ANDROID_PHONE", BotConfiguration.MiraiProtocol.ANDROID_PHONE);
        type.put("ANDROID_PAD", BotConfiguration.MiraiProtocol.ANDROID_PAD);
        type.put("ANDROID_WATCH", BotConfiguration.MiraiProtocol.ANDROID_WATCH);
        type.put("MACOS", BotConfiguration.MiraiProtocol.MACOS);
    }

    /**
     * Mirai框架的启动方法，一次性启动Mirai并开启消息监听
     */
    public void startMirai(){
        //判断一下qq数量和密码数量是否匹配
        if (qqList.length == 0 || qqList.length != pwList.length){
            throw new AngelinaException("当前配置文件中账号密码配置有误，请审视配置文件");
        }
        if (Arrays.equals(botNames, new String[]{""})) {
            throw new AngelinaException("请填写bot的名称！");
        }
        for (int i = 0; i < qqList.length; i++) {
            //循环登录所有配置的qq账号，如果有需要滑块验证的，需要单独解决
            long qq = Long.parseLong(qqList[i]);
            BotConfiguration.MiraiProtocol miraiProtocol = type.get(typeList);
            BotConfiguration botConfiguration = new BotConfiguration();
            //botConfiguration.setDeviceInfo(bot -> new MiraiDeviceGenerator().load(bot));
            botConfiguration.setProtocol(miraiProtocol);
            botConfiguration.fileBasedDeviceInfo("runFile/device.json");
            /*
            botConfiguration.setDeviceInfo(bot -> new DeviceInfo("Huawei.856832.001".getBytes(StandardCharsets.UTF_8),
                    "nova75g".getBytes(StandardCharsets.UTF_8),
                    "JEF-AN20".getBytes(StandardCharsets.UTF_8),
                    "Huawei Kirin 985".getBytes(StandardCharsets.UTF_8),
                    "Huawei".getBytes(StandardCharsets.UTF_8),
                    "Nova 7".getBytes(StandardCharsets.UTF_8),
                    "HarmonyOS 2.0".getBytes(StandardCharsets.UTF_8),
                    "Huawei/Nova/nova:7/MIRAI.200122.001/2736748:user/release-keys".getBytes(StandardCharsets.UTF_8),
                    "1BBBCCA8-0B4A-2EFC-BE95-E732C84DA5F0".getBytes(StandardCharsets.UTF_8),
                    "HarmonyOS version 2.0.0.221(C00E208R6P8)".getBytes(StandardCharsets.UTF_8),
                    "unknown".getBytes(StandardCharsets.UTF_8),
                    new DeviceInfo.Version(),
                    "T-Mobile".getBytes(StandardCharsets.UTF_8),
                    "HarmonyOS".getBytes(StandardCharsets.UTF_8),
                    "02:00:00:00:00:00".getBytes(StandardCharsets.UTF_8),
                    "02:00:00:00:00:00".getBytes(StandardCharsets.UTF_8),
                    "Strelitzia".getBytes(StandardCharsets.UTF_8),
                    "6e096dd53aa9062c".getBytes(StandardCharsets.UTF_8),
                    "342086728277870",
                    "wifi".getBytes(StandardCharsets.UTF_8)
            ));
            */
            Bot bot = BotFactory.INSTANCE.newBot(qq, pwList[i], botConfiguration);
            try {
                log.info("尝试登录 {}",qq);
                bot.login();
            } catch (Exception e) {
                e.printStackTrace();
                log.error("{} 登录失败",bot.getId());
            }
        }
        reBuildBotGroupMap();

        //监听全部qq账号所接受的消息
        GlobalEventChannel.INSTANCE.subscribeAlways(GroupMessageEvent.class, event -> {
            if (messageIdMap.get(event.getGroup().getId()) == event.getBot().getId()) {
                MessageInfo messageInfo = new MessageInfo(event, botNames);
                try {
                    groupChatController.receive(messageInfo);
                    AngelinaEventSource.getInstance().handle(messageInfo);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        //某个Bot被踢出群或退群
        GlobalEventChannel.INSTANCE.subscribeAlways(BotLeaveEvent.class, event -> {
            reBuildBotGroupMap();
        });

        //某个Bot加入了一个新群
        GlobalEventChannel.INSTANCE.subscribeAlways(BotJoinGroupEvent.class, event -> {
            messageIdMap.put(event.getGroupId(), event.getBot().getId());
            activityMapper.getEventMessage();
            MessageInfo messageInfo = new MessageInfo(event);
            try {
                eventsController.receive(messageInfo);
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        });

        //群撤回消息
        GlobalEventChannel.INSTANCE.subscribeAlways(MessageRecallEvent.GroupRecall.class, event -> {
            if (messageIdMap.get(event.getGroup().getId()) == event.getBot().getId()) {
                activityMapper.getEventMessage();
                MessageInfo messageInfo = new MessageInfo();
                messageInfo.setEvent(EventEnum.GroupRecall);
                messageInfo.setLoginQq(event.getBot().getId());
                messageInfo.setGroupId(event.getGroup().getId());
                messageInfo.setQq(event.getAuthorId());
                try {
                    eventsController.receive(messageInfo);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        //戳一戳
        GlobalEventChannel.INSTANCE.subscribeAlways(NudgeEvent.class, event -> {
            Contact subject = event.getSubject();
            if (subject instanceof Group) {
                if (messageIdMap.get(subject.getId()) == event.getBot().getId() && event.getTarget() instanceof Bot){
                    activityMapper.getEventMessage();
                    MessageInfo messageInfo = new MessageInfo();
                    messageInfo.setEvent(EventEnum.NudgeEvent);
                    messageInfo.setLoginQq(event.getBot().getId());
                    messageInfo.setGroupId(subject.getId());
                    messageInfo.setQq(event.getFrom().getId());
                    try {
                        eventsController.receive(messageInfo);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        //某人进群
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberJoinEvent.class, event -> {
            if (messageIdMap.get(event.getGroup().getId()) == event.getBot().getId()) {
                activityMapper.getEventMessage();
                MessageInfo messageInfo = new MessageInfo();
                messageInfo.setEvent(EventEnum.MemberJoinEvent);
                messageInfo.setLoginQq(event.getBot().getId());
                messageInfo.setGroupId(event.getGroup().getId());
                messageInfo.setQq(event.getMember().getId());
                messageInfo.setName(event.getMember().getNick());
                try {
                    eventsController.receive(messageInfo);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        //某人退群
        GlobalEventChannel.INSTANCE.subscribeAlways(MemberLeaveEvent.class, event -> {
            if (messageIdMap.get(event.getGroup().getId()) == event.getBot().getId()) {
                activityMapper.getEventMessage();
                MessageInfo messageInfo = new MessageInfo();
                messageInfo.setEvent(EventEnum.MemberLeaveEvent);
                messageInfo.setLoginQq(event.getBot().getId());
                messageInfo.setGroupId(event.getGroup().getId());
                messageInfo.setQq(event.getMember().getId());
                messageInfo.setName(event.getMember().getNick());
                messageInfo.setGroupName(event.getGroup().getName());
                messageInfo.setGroupOwnerName(event.getGroup().getOwner().getNick());
                try {
                    eventsController.receive(messageInfo);
                } catch (InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        //账号掉线
        GlobalEventChannel.INSTANCE.subscribeAlways(BotOfflineEvent.class, event -> {
            reBuildBotGroupMap();
        });

        //账号被禁言
        GlobalEventChannel.INSTANCE.subscribeAlways(BotMuteEvent.class, event -> {
            if (event.component1() > 60 * 60 * 24) {
                event.getGroup().quit();
            }
        });

        //账号上线
        GlobalEventChannel.INSTANCE.subscribeAlways(BotOnlineEvent.class, event -> {
            reBuildBotGroupMap();
        });

        //收到加群邀请自动忽略
        GlobalEventChannel.INSTANCE.subscribeAlways(BotInvitedJoinGroupRequestEvent.class, BotInvitedJoinGroupRequestEvent::ignore);

        //好友消息
        GlobalEventChannel.INSTANCE.subscribeAlways(FriendMessageEvent.class, event -> {
            MessageInfo messageInfo = new MessageInfo(event, botNames);
            activityMapper.getFriendMessage();
            try {
                friendChatController.receive(messageInfo);
                AngelinaEventSource.getInstance().handle2(messageInfo);
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        });

        //陌生人消息
        GlobalEventChannel.INSTANCE.subscribeAlways(StrangerMessageEvent.class, event -> {
            MessageInfo messageInfo = new MessageInfo(event, botNames);
            activityMapper.getFriendMessage();
            try {
                strangerChatController.receive(messageInfo);
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        });

        //群会话消息
        GlobalEventChannel.INSTANCE.subscribeAlways(GroupTempMessageEvent.class, event -> {
            MessageInfo messageInfo = new MessageInfo(event, botNames);
            activityMapper.getFriendMessage();
            try {
                groupTempChatController.receive(messageInfo);
                AngelinaEventSource.getInstance().handle2(messageInfo);
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 重建群号-账号映射，保证一个群只有一个账号提供服务
     */
    private void reBuildBotGroupMap() {
        //一个群号只能有一个bot提供服务
        if (status == 0) {
            status = 1;
            for (Bot bot : Bot.getInstances()) {
                if (bot.isOnline()) {
                    for (Group group : bot.getGroups()) {
                        messageIdMap.put(group.getId(), bot.getId());
                    }
                }
            }
            status = 0;
        } else {
            log.warn("is on rebuilding group-bot");
        }
    }

    /**
     * 单独分离一个方法用于制造最新请求到的群组列表
     */
    public Map<Long,Long> BotGroupMap() {
        Map<Long,Long> groupMap = new HashMap<>();
        for (Bot bot : Bot.getInstances()) {
            for (Group group : bot.getGroups()) {
                groupMap.put(group.getId(), bot.getId());
            }
        }
        return groupMap;
    }

}
