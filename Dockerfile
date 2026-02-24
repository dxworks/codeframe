FROM eclipse-temurin:17-jdk-jammy
WORKDIR /workspace
# Optional: install tools you like
RUN apt-get update && apt-get install -y bash git && rm -rf /var/lib/apt/lists/*
# Default command drops you into a shell; we’ll mount sources at runtime
CMD ["bash"]
