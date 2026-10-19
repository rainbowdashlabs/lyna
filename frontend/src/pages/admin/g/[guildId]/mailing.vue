/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {
  createMissingMailings,
  getGuildMail,
  type GuildMail,
  listMailings,
  type MailBlock,
  type MailingTemplate,
  previewMailing,
  saveMailingBlocks,
  sendTestMail,
  useDefaultMailing,
} from '~/api/admin'
import {useAdminAction} from '~/composables/useAdminAction'
import {usePageTitle} from '~/composables/usePageTitle'
import {mailingStateKey, parseMailBlocks} from '~/util/mailing'

const {t} = useI18n()
usePageTitle(t('layout.admin.mailing'))

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))

const templates = ref<MailingTemplate[]>([])
const guildMail = ref<GuildMail | null>(null)
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const editing = ref<MailingTemplate | null>(null)
const editingDefault = ref(false)
const {busy, error: actionError, run} = useAdminAction(() => t('page.admin.g.guildId.mailing.couldNotDoThat'))

async function load() {
  loading.value = true
  errorMessage.value = null
  try {
    const [mailings, guild] = await Promise.all([listMailings(guildId.value), getGuildMail(guildId.value)])
    templates.value = mailings
    guildMail.value = guild
  } catch (e) {
    errorMessage.value = (e as Error).message ?? t('page.admin.g.guildId.mailing.couldNotLoad')
  } finally {
    loading.value = false
  }
}

watch(() => route.params.guildId, next => {
  guildId.value = String(next)
  editing.value = null
  editingDefault.value = false
  void load()
})

onMounted(load)

const defaultBlocks = computed(() => parseMailBlocks(guildMail.value?.blocks ?? null))

/**
 * What the editor starts with.
 *
 * <p>A mail composed before the editor existed opens as the one HTML block it is, rather than being
 * guessed at: nobody can turn arbitrary markup back into blocks reliably, and silently rewriting
 * somebody's mail is worse than showing it as what it is. A product on the guild's default opens
 * empty, which is what saving it empty keeps it on.
 */
function blocksOf(template: MailingTemplate): MailBlock[] {
  if (template.usesDefault) return []
  if (template.blocks) return parseMailBlocks(template.blocks)
  return template.mailText ? [{type: 'raw', html: template.mailText}] : []
}

async function addMissing() {
  await run(() => createMissingMailings(guildId.value))
  await load()
}

async function backToDefault(template: MailingTemplate) {
  await run(() => useDefaultMailing(guildId.value, template.id))
  editing.value = null
  await load()
}

function close() {
  editing.value = null
  editingDefault.value = false
}
</script>

<template>
  <div>
    <PageHeader class="mb-4">
      {{ t('page.admin.g.guildId.mailing.mailingTemplates') }}
    </PageHeader>
    <MutedText class="mb-4 block" size="sm">
      {{ t('page.admin.g.guildId.mailing.theMailSentWhenSomebodyBuys') }}
    </MutedText>

    <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
      <GuildMailEditor
          v-if="editingDefault && guildMail"
          :guild-id="guildId"
          :guild-mail="guildMail"
          :values="templates[0]?.values"
          @changed="load"
          @close="close"
      />

      <MailingEditorPanel
          v-else-if="editing"
          :blocks="blocksOf(editing)"
          :preview="(blocks: MailBlock[]) => previewMailing(guildId, editing!.id, blocks)"
          :save="(blocks: MailBlock[]) => saveMailingBlocks(guildId, editing!.id, blocks)"
          :send-test="(address: string) => sendTestMail(guildId, editing!.id, address)"
          :starter="{label: t('page.admin.g.guildId.mailing.loadDefault'), blocks: defaultBlocks}"
          :subtitle="`${editing.name} · ${t(mailingStateKey(editing))}`"
          :title="editing.productName"
          :values="editing.values"
          @close="close"
          @saved="load"
      >
        <template #actions>
          <SecondaryButton v-if="!editing.usesDefault" :disabled="busy" compact @click="backToDefault(editing)">
            {{ t('page.admin.g.guildId.mailing.useDefault') }}
          </SecondaryButton>
        </template>
      </MailingEditorPanel>

      <MailingOverview
          v-else
          :action-error="actionError"
          :busy="busy"
          :guild-id="guildId"
          :guild-mail="guildMail"
          :templates="templates"
          @add-missing="addMissing"
          @edit="template => editing = template"
          @edit-default="editingDefault = true"
          @reload="load"
      />
    </AsyncSection>
  </div>
</template>
