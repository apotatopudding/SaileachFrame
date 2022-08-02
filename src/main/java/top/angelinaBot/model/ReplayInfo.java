package top.angelinaBot.model;

import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.Dice;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.utils.ExternalResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author strelitzia
 * @Date 2022/04/03
 * 回复消息结构化Bean
 **/
@Slf4j
public class ReplayInfo {
    //登录QQ
    Long loginQQ;
    //qq
    Long qq;
    //昵称
    String name;
    //群号
    Long groupId;
    //文字内容
    String replayMessage;
    //图片内容
    List<ExternalResource> replayImg = new ArrayList<>();
    //语音内容
    List<ExternalResource> replayAudio = new ArrayList<>();
    //踢出群
    Long AT;
    //获取@的人
    String kick;
    //禁言
    Integer muted;
    //禁言全体
    Boolean isMutedAll = false;
    //戳一戳
    Long nudged;
    //查询机器人权限
    Boolean permission = false;
    //发送一个随机骰子消息
    Dice dice;
    //撤回时间（目前仅设置给带图消息）
    Integer recallTime;

    public ReplayInfo(MessageInfo messageInfo) {
        this.loginQQ = messageInfo.getLoginQq();
        this.groupId = messageInfo.getGroupId();
        this.qq = messageInfo.getQq();
        this.name = messageInfo.getName();

    }

    public ReplayInfo() {

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getLoginQQ() {
        return loginQQ;
    }

    public void setLoginQQ(Long loginQQ) {
        this.loginQQ = loginQQ;
    }

    public Long getQq() {
        return qq;
    }

    public void setQq(Long qq) {
        this.qq = qq;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getAT() { return AT; }

    public void setAT(Long AT) { this.AT = AT; }

    public String getKick() {
        return kick;
    }

    public void setKick(String kick) {
        this.kick = kick;
    }

    public Integer getMuted() {
        return muted;
    }

    public void setMuted(Integer muted) {
        this.muted = muted;
    }

    public Boolean getMutedAll() { return isMutedAll; }

    public void setMutedAll(Boolean mutedAll) { isMutedAll = mutedAll; }

    public Long getNudged() { return nudged; }

    public void setNudged(Long nudged) { this.nudged = nudged; }

    public String getReplayMessage() {
        return replayMessage;
    }

    public void setReplayMessage(String replayMessage) {
        this.replayMessage = replayMessage;
    }

    public Boolean getPermission() { return permission; }

    public void setPermission(Boolean permission) { this.permission = permission; }

    public Dice getDice() { return dice; }

    public void setDice(Dice dice) { this.dice = dice; }

    public Integer getRecallTime() { return recallTime; }

    public void setRecallTime(Integer recallTime) { this.recallTime = recallTime; }

    /**
     * 获取ReplayInfo的图片集合
     * @return 返回图片的输入流集合
     */
    public List<ExternalResource> getReplayImg() {
        return replayImg;
    }

    /**
     * 以BufferImage格式插入图片
     * @param bufferedImage 图片BufferedImage
     */
    public void setReplayImg(BufferedImage bufferedImage) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()){
            ImageIO.write(bufferedImage, "jpg", os);
            InputStream inputStream = new ByteArrayInputStream(os.toByteArray());
            ExternalResource externalResource = ExternalResource.create(inputStream).toAutoCloseable();
            replayImg.add(externalResource);
            inputStream.close();
        } catch (IOException e) {
            log.error("BufferImage读取IO流失败");
        }
    }

    /**
     * 以文件格式插入图片
     * @param file 文件File
     */
    public void setReplayImg(File file) {
        try (InputStream inputStream = new FileInputStream(file)){
            ExternalResource externalResource = ExternalResource.create(inputStream).toAutoCloseable();
            replayImg.add(externalResource);
        } catch (IOException e) {
            log.error("File读取IO流失败");
        }
    }

    /**
     * 以url形式插入图片
     * @param url 图片url
     */
    public void setReplayImg(String url) {
        try {
            URL u = new URL(url);
            HttpURLConnection httpUrl = (HttpURLConnection) u.openConnection();
            httpUrl.connect();
            try (InputStream is = httpUrl.getInputStream()){
                ExternalResource externalResource = ExternalResource.create(is).toAutoCloseable();
                replayImg.add(externalResource);
            }
        } catch (IOException e) {
            log.error("读取图片URL失败");
        }
    }

    /**
     * 获取ReplayInfo的语音集合
     * @return 返回语音的输入流集合
     */
    public List<ExternalResource> getReplayAudio() {
        return replayAudio;
    }

    /**
     * 以文件格式插入语音
     * @param file 文件File
     */
    public void setReplayAudio(File file) {
        try (InputStream inputStream = new FileInputStream(file)){
            ExternalResource externalResource  = ExternalResource.create(inputStream).toAutoCloseable();
            replayAudio.add(externalResource);
        } catch (IOException e) {
            log.error("File读取IO流失败");
        }
    }
}

