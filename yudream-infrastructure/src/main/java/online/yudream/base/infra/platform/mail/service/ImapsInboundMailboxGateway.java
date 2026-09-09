package online.yudream.base.infra.platform.mail.service;

import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.search.BodyTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mail.service.InboundMailboxGateway;
import online.yudream.base.domain.platform.mail.valobj.InboundMailDetail;
import online.yudream.base.domain.platform.mail.valobj.InboundMailSummary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * IMAPS 只读收件箱网关。每次查询短连接，不缓存 Store。
 */
@Service
public class ImapsInboundMailboxGateway implements InboundMailboxGateway {

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final ObjectProvider<InboundMailCapabilityProvider> capabilityProvider;

    public ImapsInboundMailboxGateway(ObjectProvider<InboundMailCapabilityProvider> capabilityProvider) {
        this.capabilityProvider = capabilityProvider;
    }

    @Override
    public PageResult<InboundMailSummary> page(int page, int size, String keyword) {
        return withFolder(folder -> {
            UIDFolder uidFolder = uidFolder(folder);
            Message[] messages;
            int total;
            if (StringUtils.hasText(keyword)) {
                Message[] found = search(folder, keyword);
                total = found.length;
                messages = newestSlice(found, page, size);
            } else {
                total = Math.max(folder.getMessageCount(), 0);
                messages = newestSlice(folder, page, size, total);
            }
            if (messages.length == 0) {
                return new PageResult<>(List.of(), total, page, size);
            }
            fetchEnvelopes(folder, messages);
            List<InboundMailSummary> records = new ArrayList<>(messages.length);
            for (int i = messages.length - 1; i >= 0; i--) {
                records.add(InboundMailMimeParser.toSummary(messages[i], uidFolder.getUID(messages[i])));
            }
            return new PageResult<>(records, total, page, size);
        });
    }

    @Override
    public Optional<InboundMailDetail> findByUid(long uid) {
        return withFolder(folder -> {
            Message message = uidFolder(folder).getMessageByUID(uid);
            if (message == null) {
                return Optional.empty();
            }
            return Optional.of(InboundMailMimeParser.toDetail(message, uid));
        });
    }

    private Message[] newestSlice(Folder folder, int page, int size, int total) throws Exception {
        if (total <= 0) {
            return new Message[0];
        }
        int newest = total - (page - 1) * size;
        if (newest <= 0) {
            return new Message[0];
        }
        int oldest = Math.max(1, newest - size + 1);
        return folder.getMessages(oldest, newest);
    }

    private Message[] newestSlice(Message[] found, int page, int size) {
        int total = found.length;
        int fromIndex = Math.max(0, total - page * size);
        int toIndex = Math.max(0, total - (page - 1) * size);
        if (fromIndex >= toIndex) {
            return new Message[0];
        }
        Message[] slice = new Message[toIndex - fromIndex];
        System.arraycopy(found, fromIndex, slice, 0, slice.length);
        return slice;
    }

    private Message[] search(Folder folder, String keyword) throws Exception {
        String term = keyword.trim();
        SearchTerm search = new OrTerm(new SearchTerm[]{
                new SubjectTerm(term),
                new FromStringTerm(term),
                new BodyTerm(term)
        });
        Message[] found = folder.search(search);
        return found == null ? new Message[0] : found;
    }

    private void fetchEnvelopes(Folder folder, Message[] messages) throws Exception {
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.ENVELOPE);
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(FetchProfile.Item.CONTENT_INFO);
        profile.add(UIDFolder.FetchProfileItem.UID);
        folder.fetch(messages, profile);
    }

    private UIDFolder uidFolder(Folder folder) {
        if (folder instanceof UIDFolder uidFolder) {
            return uidFolder;
        }
        throw new BizException("当前 IMAP 文件夹不支持 UID 读取");
    }

    private <T> T withFolder(FolderCallback<T> callback) {
        InboundMailCapabilityProvider provider = provider();
        if (provider == null || !provider.active()) {
            throw new BizException("入站邮箱能力未启用，请先在平台能力中启用");
        }
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        props.put("mail.imaps.timeout", String.valueOf(READ_TIMEOUT_MS));
        props.put("mail.imaps.partialfetch", "false");
        String folderName = provider.configValue(InboundMailCapabilityProvider.CONFIG_FOLDER);
        try (Store store = Session.getInstance(props).getStore("imaps")) {
            store.connect(
                    provider.configValue(InboundMailCapabilityProvider.CONFIG_HOST),
                    port(provider.configValue(InboundMailCapabilityProvider.CONFIG_PORT)),
                    provider.configValue(InboundMailCapabilityProvider.CONFIG_USERNAME),
                    provider.configValue(InboundMailCapabilityProvider.CONFIG_PASSWORD)
            );
            Folder folder = store.getFolder(StringUtils.hasText(folderName) ? folderName : "INBOX");
            try {
                folder.open(Folder.READ_ONLY);
                return callback.apply(folder);
            } finally {
                if (folder.isOpen()) {
                    folder.close(false);
                }
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("读取入站邮箱失败：" + rootMessage(ex));
        }
    }

    private InboundMailCapabilityProvider provider() {
        return capabilityProvider == null ? null : capabilityProvider.getIfAvailable();
    }

    private static int port(String raw) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw.trim());
            return value > 0 && value <= 65535 ? value : 993;
        } catch (NumberFormatException ignored) {
            return 993;
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage()) ? current.getMessage() : error.getClass().getSimpleName();
    }

    @FunctionalInterface
    private interface FolderCallback<T> {
        T apply(Folder folder) throws Exception;
    }
}
