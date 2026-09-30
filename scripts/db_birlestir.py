"""Merge another server's netmovies.db into this one (heal after both servers ran).

watch_history: newest updated_at wins per content_key.
favorites / user_lists: union (a delete made on only one side comes back).

Usage (inside the stream container): python db_birlestir.py /data/netmovies.db /data/_diger.db
"""
import sqlite3
import sys


def merge(dst_path: str, src_path: str) -> None:
    con = sqlite3.connect(dst_path)
    con.execute("ATTACH DATABASE ? AS src", (src_path,))
    before = {t: con.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
              for t in ("watch_history", "favorites", "user_lists")}
    cols = [r[1] for r in con.execute("PRAGMA table_info(watch_history)")]
    col_list = ", ".join(cols)
    with con:
        con.execute(
            f"INSERT OR REPLACE INTO watch_history ({col_list}) "
            f"SELECT {col_list} FROM src.watch_history s WHERE NOT EXISTS ("
            "SELECT 1 FROM watch_history d WHERE d.content_key = s.content_key "
            "AND COALESCE(d.updated_at, 0) >= COALESCE(s.updated_at, 0))"
        )
        for table in ("favorites", "user_lists"):
            tcols = ", ".join(r[1] for r in con.execute(f"PRAGMA table_info({table})"))
            con.execute(f"INSERT OR IGNORE INTO {table} ({tcols}) SELECT {tcols} FROM src.{table}")
    for t, n in before.items():
        print(f"{t}: {n} -> {con.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0]}")
    con.close()


if __name__ == "__main__":
    merge(sys.argv[1], sys.argv[2])
