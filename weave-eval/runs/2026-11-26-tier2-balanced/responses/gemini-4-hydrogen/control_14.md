The simplest way is using slice notation with a step of `-1`:

```python
text = "hello"
reversed_text = text[::-1]  # "olleh"
```

You can also use `"".join(reversed(text))` if you prefer a more explicit, readable form.
