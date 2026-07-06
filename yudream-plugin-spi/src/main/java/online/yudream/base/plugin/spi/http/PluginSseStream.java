package online.yudream.base.plugin.spi.http;

public interface PluginSseStream {

    void subscribe(Subscriber subscriber);

    void unsubscribe(Subscriber subscriber);

    interface Subscriber {
        void send(String event, Object data);

        void complete();

        void error(Throwable throwable);
    }
}
