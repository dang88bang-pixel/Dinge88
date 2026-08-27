"""Setzt die Test-Datenbank UNGEHEND vor dem Import des Backend-Moduls,
damit `import main` (ruft init_db() auf) keine secureguard.db im Repo-Root erzeugt."""
import os
import sys
import tempfile

os.environ.setdefault(
    "DATABASE_PATH", os.path.join(tempfile.gettempdir(), "secureguard_test.db")
)
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
