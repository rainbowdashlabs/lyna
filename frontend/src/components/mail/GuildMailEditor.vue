/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import {
  type GuildMail,
  type MailBlock,
  previewGuildMail,
  resetGuildMailBlocks,
  saveGuildMailBlocks,
  sendGuildTestMail,
} from '~/api/admin'
import {useAdminAction} from '~/composables/useAdminAction'
import {parseMailBlocks} from '~/util/mailing'

/**
 * The guild's default purchase mail, which every product without a mail of its own sends. The
 * default Lyna ships can be loaded back in, or gone back to outright.
 */
const props = defineProps<{ guildId: string, guildMail: GuildMail, values?: Record<string, string> }>()

const emit = defineEmits<{ close: [], changed: [] }>()

const {t} = useI18n()
const {busy, run} = useAdminAction(() => t('page.admin.g.guildId.mailing.couldNotDoThat'))

const blocks = computed(() => parseMailBlocks(props.guildMail.blocks))
const builtIn = computed(() => parseMailBlocks(props.guildMail.builtIn))

async function reset() {
  await run(() => resetGuildMailBlocks(props.guildId))
  emit('changed')
  emit('close')
}
</script>

<template>
  <MailingEditorPanel
      :blocks="blocks"
      :preview="(edited: MailBlock[]) => previewGuildMail(guildId, edited)"
      :save="(edited: MailBlock[]) => saveGuildMailBlocks(guildId, edited)"
      :send-test="(address: string) => sendGuildTestMail(guildId, address)"
      :starter="{label: t('page.admin.g.guildId.mailing.loadShipped'), blocks: builtIn}"
      :subtitle="t('page.admin.g.guildId.mailing.defaultExplain')"
      :title="t('page.admin.g.guildId.mailing.guildDefault')"
      :values="values"
      @close="emit('close')"
      @saved="emit('changed')"
  >
    <template #actions>
      <SecondaryButton v-if="guildMail.custom" :disabled="busy" compact @click="reset">
        {{ t('page.admin.g.guildId.mailing.resetToShipped') }}
      </SecondaryButton>
    </template>
  </MailingEditorPanel>
</template>
