package cn.ieclipse.smartqq;

import java.util.Map;

import org.eclipse.jface.preference.IPreferenceStore;

import com.scienjus.smartqq.client.SmartQQClient;
import com.scienjus.smartqq.model.DiscussFrom;
import com.scienjus.smartqq.model.DiscussInfo;
import com.scienjus.smartqq.model.DiscussMessage;
import com.scienjus.smartqq.model.DiscussUser;
import com.scienjus.smartqq.model.FriendFrom;
import com.scienjus.smartqq.model.GroupFrom;
import com.scienjus.smartqq.model.GroupInfo;
import com.scienjus.smartqq.model.GroupMessage;
import com.scienjus.smartqq.model.GroupUser;
import com.scienjus.smartqq.model.QQContact;
import com.scienjus.smartqq.model.QQMessage;
import com.scienjus.smartqq.model.UserInfo;

import cn.ieclipse.smartim.IMPlugin;
import cn.ieclipse.smartim.IMRobotCallback;
import cn.ieclipse.smartim.model.IContact;
import cn.ieclipse.smartim.model.IFrom;
import cn.ieclipse.smartim.model.impl.AbstractFrom;
import cn.ieclipse.smartim.model.impl.AbstractMessage;
import cn.ieclipse.smartim.preferences.RobotPreferencePage;
import cn.ieclipse.util.StringUtils;

public class QQRobotCallback extends IMRobotCallback {
    
    private QQChatConsole console;
    private QQContactView fContactView;
    
    public QQRobotCallback(QQContactView fContactView) {
        this.fContactView = fContactView;
    }
    
    @Override
    public void onContactChanged(IContact contact) {
        if (!isEnable()) {
            return;
        }
        try {
            console = (QQChatConsole) fContactView
                    .findConsoleById(contact.getUin(), false);
            // TODO
        } catch (Exception e) {
            IMPlugin.getDefault().log("机器人回应异常", e);
        }
    }
    
    @Override
    public void onReceiveMessage(AbstractMessage message, AbstractFrom from) {
        if (!isEnable()) {
            return;
        }
        try {
            console = (QQChatConsole) fContactView
                    .findConsoleById(from.getContact().getUin(), false);
            answer(from, (QQMessage) message);
        } catch (Exception e) {
            IMPlugin.getDefault().log("机器人回应异常", e);
        }
    }
    
    @Override
    public void onReceiveError(Throwable e) {
        // TODO Auto-generated method stub
        
    }
    
    public void answer(AbstractFrom from, QQMessage m) {
        IPreferenceStore store = IMPlugin.getDefault().getPreferenceStore();
        String robotName = getRobotName();
        SmartQQClient client = fContactView.getClient();
        // auto reply friend
        if (from instanceof FriendFrom) {
            if (store.getBoolean(RobotPreferencePage.FRIEND_REPLY_ANY)) {
                String reply = getReply(m.getContent(),
                        (QQContact) from.getContact(), null);
                if (reply != null) {
                    String input = robotName + reply;
                    if (console == null) {
                        client.sendMessageToFriend(m.getUserId(), input);
                    }
                    else {
                        console.send(input);
                    }
                }
            }
            return;
        }
        else if (from instanceof GroupFrom) {
            GroupFrom gf = (GroupFrom) from;
            String gName = gf.getGroup().getName();
            // QQContact qqContact = client.getGroup(gf.getGroup().getId());
            if (from.isNewbie()) {
                String welcome = store
                        .getString(RobotPreferencePage.GROUP_WELCOME);
                if (welcome != null && !welcome.isEmpty()) {
                    GroupInfo info = gf.getGroup();
                    GroupUser gu = gf.getGroupUser();
                    String input = welcome;
                    if (gu != null) {
                        input = input.replace("{user}", gu.getName());
                    }
                    if (info != null) {
                        input = input.replace("{memo}", info.getMemo());
                    }
                    if (console != null) {
                        console.send(robotName + input);
                    }
                    else {
                        client.sendMessageToGroup(gf.getGroup().getId(),
                                robotName + input);
                    }
                }
            }
            
            // @
            if (atMe(from, m)) {
                String msg = m.getContent(true).trim();
                String reply = getReply(msg, from.getMember(), gName);
                if (reply != null) {
                    String input = robotName + "@" + from.getMember().getName()
                            + SEP + reply;
                    if (console != null) {
                        console.send(input);
                    }
                    else {
                        if (m instanceof GroupMessage) {
                            client.sendMessageToGroup(
                                    ((GroupMessage) m).getGroupId(), input);
                        }
                        else if (m instanceof DiscussMessage) {
                            client.sendMessageToDiscuss(
                                    ((DiscussMessage) m).getDiscussId(), input);
                        }
                    }
                }
                return;
            }
            // replay any
            if (store.getBoolean(RobotPreferencePage.GROUP_REPLY_ANY)) {
                if (from.isNewbie() || isMySend(m.getUserId())) {
                    return;
                }
                if (console == null) {
                    return;
                }
                
                if (from instanceof FriendFrom) {
                    return;
                }
                else if (from instanceof GroupFrom) {
                    if (((GroupFrom) from).getGroupUser().isUnknown()) {
                        return;
                    }
                }
                else if (from instanceof DiscussFrom) {
                    if (((DiscussFrom) from).getDiscussUser().isUnknown()) {
                        return;
                    }
                }
                
                String reply = getReply(m.getContent(), from.getMember(),
                        gName);
                if (reply != null) {
                    String input = robotName + "@" + from.getName() + SEP
                            + reply;
                    if (console != null) {
                        console.send(input);
                    }
                }
            }
        } // end group
        
    }
    
    private Map<String, Object> getParams(String text, IContact contact,
            String groupId) {
        String key = getTuringApiKey();
        if (StringUtils.isEmpty(key)) {
            return null;
        }
        TuringRequestV2Builder builder = new TuringRequestV2Builder(key);
        builder.setText(text);
        String uid = encodeUid(contact.getName());
        String uname = contact.getName();
        String gid = groupId == null ? null : encodeUid(groupId);
        builder.setUserInfo(uid, uname, gid);
        // builder.setLocation(contact, contact.Province, null);
        return builder.build();
    }
    
    private String getReply(String text, IContact contact, String groupId) {
        if (StringUtils.isEmpty(text)) {
            String reply = IMPlugin.getDefault().getPreferenceStore()
                    .getString(RobotPreferencePage.ROBOT_EMPTY);
            if (!StringUtils.isEmpty(reply)) {
                return reply;
            }
            return null;
        }
        Map<String, Object> params = getParams(text, contact, groupId);
        if (params != null) {
            return getTuringReply(TURING_API_V2, params);
        }
        return null;
    }
    
    private boolean atMe(IFrom from, QQMessage m) {
        if (m.getAts() != null) {
            if (m instanceof GroupMessage) {
                GroupInfo info = ((GroupFrom) from).getGroup();
                UserInfo me = getAccount();
                long uin = Long.parseLong(me.getUin());
                GroupUser me2 = info.getGroupUser(uin);
                if (m.hasAt(me2.getName()) || m.hasAt(me.getNick())) {
                    return true;
                }
            }
            
            else if (m instanceof DiscussMessage) {
                DiscussInfo info = ((DiscussFrom) from).getDiscuss();
                UserInfo me = getAccount();
                long uin = Long.parseLong(me.getUin());
                DiscussUser me2 = info.getDiscussUser(uin);
                if (m.hasAt(me2.getName()) || m.hasAt(me.getNick())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private UserInfo getAccount() {
        UserInfo me = getClient().getAccount();
        return me;
    }
    
    private SmartQQClient getClient() {
        return fContactView.getClient();
    }
    
    private boolean isMySend(long uin) {
        UserInfo me = getAccount();
        if (me != null && me.getUin().equals(String.valueOf(uin))) {
            return true;
        }
        return false;
    }
}
