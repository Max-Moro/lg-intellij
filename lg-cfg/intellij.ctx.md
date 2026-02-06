{% if scope:local %}{% if tag:agent %}
${tpl:agent/index}

---
{% endif %}
${md:../cli/README}

---
{% endif %}
${md:README}

---
{% if tag:review %}
# Modified Source Code of LG IntelliJ Platform Plugin in Current Branch

${review}

{% else %}
# Source Code of LG IntelliJ Platform Plugin

${src}

{% endif %}
---

${md@self:architecture, if:(TAGSET:intellij-plugin:state-pce OR TAGSET:intellij-plugin:state-engine)}
{% if task AND scope:local %}
---

# Current task description

${task}{% endif %}
{% if scope:local AND tag:agent %}
${tpl:agent/footer}
{% endif %}
