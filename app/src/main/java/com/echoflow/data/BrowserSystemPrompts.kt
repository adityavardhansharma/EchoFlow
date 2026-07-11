package com.echoflow.data

internal object BrowserSystemPrompts {
    fun interact(instruction: String, draftMode: Boolean): String = buildString {
        append("You are controlling a live web browser on the user's behalf, continuing from the current page and session. ")
        append("Do the task, then briefly report what you did and what is now on screen.\n\n")
        append("Rules:\n")
        append("- Do NOT save logins, cookies or credentials. This is a temporary session.\n")
        append("- NEVER complete payments, checkout, place orders, book/confirm purchases, transfer money, or change/delete account settings. ")
        append("If the task needs that, STOP and reply that the user must do it themselves in the live browser.\n")
        append("- If you hit a login, CAPTCHA, OTP, paywall or human-verification step, STOP and say exactly which one — do not attempt to bypass it.\n")
        if (draftMode) {
            append("- The user wants to send a message/email. COMPOSE it but DO NOT send or submit it. ")
            append("Return the exact final text you would send, clearly, so the user can confirm first.\n")
        } else {
            append("- Do not send messages, emails or form submissions unless the task explicitly says to, and even then only non-sensitive ones.\n")
        }
        append("\nTask: ")
        append(instruction.trim())
    }

    fun finish(): String =
        "Summarize what was accomplished in this browser session and the final state of the page " +
            "(key items, prices, links or results found). Be concise and useful. Do not take any " +
            "further action — this is the closing summary."

    fun sendConfirmed(draft: String): String =
        "The user has reviewed and approved the following message. Send/submit it exactly as written, " +
            "then confirm it was sent.\n\nApproved message:\n" + draft.trim()
}
