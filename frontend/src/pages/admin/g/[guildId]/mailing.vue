/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {listMailings, type MailBlock, type MailingTemplate} from '~/api/admin'

const {t} = useI18n()

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))

const templates = ref<MailingTemplate[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const editing = ref<MailingTemplate | null>(null)

async function load() {
  loading.value = true
  errorMessage.value = null
  try {
    templates.value = await listMailings(guildId.value)
  } catch (e) {
    errorMessage.value = (e as Error).message ?? 'Failed to load the templates.'
  } finally {
    loading.value = false
  }
}

watch(() => route.params.guildId, next => {
  guildId.value = String(next)
  editing.value = null
  void load()
})

onMounted(load)

/**
 * What the editor starts with.
 *
 * <p>A mail composed before the editor existed opens as the one HTML block it is, rather than being
 * guessed at: nobody can turn arbitrary markup back into blocks reliably, and silently rewriting
 * somebody's mail is worse than showing it as what it is.
 */
function blocksOf(template: MailingTemplate): MailBlock[] {
  if (template.blocks) {
    try {
      return JSON.parse(template.blocks) as MailBlock[]
    } catch {
      return []
    }
  }
  return template.mailText ? [{type: 'raw', html: template.mailText}] : []
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

    <AsyncSection
        :empty="!loading && templates.length === 0"
        :error="errorMessage ?? undefined"
        :loading="loading"
        empty-message="No product in this guild has a mail template yet."
    >
      <MailingEditorPanel
          v-if="editing"
          :blocks="blocksOf(editing)"
          :guild-id="guildId"
          :template="editing"
          @close="editing = null"
          @saved="load"
      />
      <ul v-else class="space-y-2">
        <li v-for="template in templates" :key="template.id">
          <NeutralContainer>
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div>
                <div class="font-medium">{{ template.productName }}</div>
                <MutedText tag="div">
                  {{ template.name }}
                  <template v-if="!template.blocks"> {{ t('page.admin.g.guildId.mailing.middotWrittenBeforeTheEditor') }}</template>
                </MutedText>
              </div>
              <PrimaryButton compact @click="editing = template">{{ t('common.edit') }}</PrimaryButton>
            </div>
          </NeutralContainer>
        </li>
      </ul>
    </AsyncSection>
  </div>
</template>
