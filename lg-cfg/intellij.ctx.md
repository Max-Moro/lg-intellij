# ${md:README}

---
{% if tag:review %}
# Измененный исходный код LG IntelliJ Platform Plugin в текущей ветке
{% else %}
# Исходный код LG IntelliJ Platform Plugin
{% endif %}

${src}

---

# ${md@self:ai-integration/common}

# ${md:decompiled/DECOMPILATION-REPORT}

# ${md:decompiled/INTEGRATION-TARGETS}

{% if task AND scope:local %}
---

# Описание текущей задачи

${task}{% endif %}