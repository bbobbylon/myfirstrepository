# ---- Stage 1: build the dependency layer ------------------------------------
# Dependencies are installed into a virtualenv in their own stage so that the
# runtime image does not carry pip's cache or any build toolchain.
FROM python:3.11-slim AS build
WORKDIR /build

# Copy ONLY the requirements first. Docker caches layers, so as long as this file
# is unchanged the install is reused even when the source changes - copying
# everything up front would reinstall on every edit.
COPY requirements.txt .
RUN python -m venv /opt/venv && /opt/venv/bin/pip install --no-cache-dir -r requirements.txt

# ---- Stage 2: runtime --------------------------------------------------------
FROM python:3.11-slim AS runtime
WORKDIR /app

# Run as a non-root user. If the application is ever compromised the attacker
# lands as an unprivileged account rather than as root inside the container.
RUN useradd --create-home --shell /bin/false app

COPY --from=build /opt/venv /opt/venv
COPY src/ ./src/

ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONPATH=/app/src \
    PYTHONUNBUFFERED=1

USER app
EXPOSE 8084

CMD ["uvicorn", "birthpath.main:app", "--host", "0.0.0.0", "--port", "8084"]
