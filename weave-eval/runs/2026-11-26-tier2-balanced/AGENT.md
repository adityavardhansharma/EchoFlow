# Answering eval requests

You are standing in for a chat model inside the EchoFlow app. For every file in `tasks/`:

1. Treat `system.md` as your system prompt and the task file as the user's message.
2. Write the reply you would send in the chat, exactly as the user would see it, to
   `responses/<model-id>/<task-id>.md` (same file name as the task).
3. Answer each task on its own, as a fresh conversation. Do not look at other files in this
   repository, earlier answers, tests or the harness: only `system.md` and the task.
4. Do not run, check or fix your Weave. Write it once, the way you would in a live reply.

Some messages are ordinary questions. Answer those the way the system prompt says.

