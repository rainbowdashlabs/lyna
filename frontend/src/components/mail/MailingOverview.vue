/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {GuildMail, MailingTemplate} from '~/api/admin'
import {mailingStateKey} from '~/util/mailing'

/**
 * A guild's purchase mails at a glance: its default, where the default points buyers, the products
 * that cannot be sold through a receipt yet, and each product's own mail.
 */
defineProps<{
  guildId: string
  guildMail: GuildMail | null
  templates: MailingTemplate[]
  busy: boolean
  actionError: string | null
}>()

const emit = defineEmits<{
  edit: [template: MailingTemplate]
  editDefault: []
  addMissing: []
  reload: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-6">
    <section class="space-y-3">
      <NeutralContainer>
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <div class="font-medium">{{ t('page.admin.g.guildId.mailing.guildDefault') }}</div>
            <MutedText tag="div">
              {{ guildMail?.custom ? t('page.admin.g.guildId.mailing.customDefault') : t('page.admin.g.guildId.mailing.shippedDefault') }}
            </MutedText>
          </div>
          <PrimaryButton compact @click="emit('editDefault')">{{ t('common.edit') }}</PrimaryButton>
        </div>
      </NeutralContainer>
      <NeutralContainer v-if="guildMail">
        <CardHeader>{{ t('page.admin.g.guildId.mailing.contact') }}</CardHeader>
        <GuildMailContactForm
            :key="guildId"
            :discord-url="guildMail.discordUrl"
            :guild-id="guildId"
            :links="guildMail.links"
            @saved="emit('reload')"
        />
      </NeutralContainer>
    </section>

    <MissingMailNotice
        v-if="guildMail?.productsWithoutMail.length"
        :busy="busy"
        :products="guildMail.productsWithoutMail"
        @add-missing="emit('addMissing')"
    />
    <MutedText v-if="actionError" class="block text-error" size="sm">{{ actionError }}</MutedText>

    <ul class="space-y-2">
      <li v-for="template in templates" :key="template.id">
        <NeutralContainer>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <div class="font-medium">{{ template.productName }}</div>
              <MutedText tag="div">{{ template.name }} · {{ t(mailingStateKey(template)) }}</MutedText>
            </div>
            <PrimaryButton compact @click="emit('edit', template)">{{ t('common.edit') }}</PrimaryButton>
          </div>
        </NeutralContainer>
      </li>
    </ul>
  </div>
</template>
