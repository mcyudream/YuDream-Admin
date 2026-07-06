import json
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) < 4:
        print("usage: add_knowledge.py <rules|utilities|pitfalls> <key> <text>")
        return 2

    section, key, text = sys.argv[1], sys.argv[2], " ".join(sys.argv[3:]).strip()
    if section not in {"rules", "utilities", "pitfalls"}:
        print("section must be one of: rules, utilities, pitfalls")
        return 2

    skill_dir = Path(__file__).resolve().parents[1]
    data_path = skill_dir / "references" / "knowledge.json"
    data = json.loads(data_path.read_text(encoding="utf-8"))
    entries = data.setdefault(section, [])

    for entry in entries:
        if entry.get("key") == key:
            entry["text"] = text
            break
    else:
        entries.append({"key": key, "text": text})

    data_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"saved {section}.{key}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
