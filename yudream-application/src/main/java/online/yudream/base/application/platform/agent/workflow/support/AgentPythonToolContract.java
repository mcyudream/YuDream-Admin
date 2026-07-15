package online.yudream.base.application.platform.agent.workflow.support;

public final class AgentPythonToolContract {
    private AgentPythonToolContract() {
    }

    public static String wrap(String userCode) {
        return userCode + """


                # YuDream Agent tool adapter. Tool authors only implement run(dict) -> dict.
                import json as _yudream_json
                import sys as _yudream_sys

                if "run" not in globals() or not callable(run):
                    raise TypeError("Python Agent tool must define run(params: dict) -> dict")

                _yudream_raw = _yudream_sys.stdin.read()
                _yudream_params = _yudream_json.loads(_yudream_raw) if _yudream_raw.strip() else {}
                if not isinstance(_yudream_params, dict):
                    raise TypeError("Agent tool input must be a dict")
                _yudream_result = run(_yudream_params)
                if not isinstance(_yudream_result, dict):
                    raise TypeError("Agent tool run() must return a dict")
                _yudream_sys.stdout.write(_yudream_json.dumps(_yudream_result, ensure_ascii=False))
                """;
    }
}
