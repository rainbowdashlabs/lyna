<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {
  changePassword,
  deleteAccount,
  endOtherSessions,
  listSessions,
  overview,
  revokeSession,
  unlinkDiscord,
  type Overview,
  type SessionRow,
} from '~/api/account'
import {useSession} from '~/composables/useSession'
import PrimaryButton from '~/components/button/PrimaryButton.vue'
import Spinner from '~/components/feedback/Spinner.vue'

const {t} = useI18n()

definePageMeta({layout: 'account'})

const router = useRouter()
const {clear, hydrate} = useSession()

const summary = ref<Overview | null>(null)
const sessions = ref<SessionRow[]>([])
const loading = ref(true)

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordBusy = ref(false)
const passwordMessage = ref<string | null>(null)
const passwordError = ref<string | null>(null)

const deleteConfirm = ref('')
const deleteBusy = ref(false)
const deleteError = ref<string | null>(null)

async function refresh() {
  loading.value = true
  try {
    summary.value = await overview()
    sessions.value = await listSessions()
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

async function doChangePassword() {
  passwordError.value = null
  passwordMessage.value = null
  if (newPassword.value.length < 8) {
    passwordError.value = t('auth.passwordTooShort')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    passwordError.value = t('auth.passwordsDiffer')
    return
  }
  passwordBusy.value = true
  try {
    await changePassword(summary.value?.account.hasPassword ? currentPassword.value : null, newPassword.value)
    passwordMessage.value = t('page.account.security.passwordUpdated')
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    await refresh()
  } catch (e) {
    const error = e as {response?: {data?: string}}
    passwordError.value = typeof error.response?.data === 'string' ? error.response.data : 'Password change failed.'
  } finally {
    passwordBusy.value = false
  }
}

async function doUnlinkDiscord() {
  if (!confirm('Unlinking will hide all licenses tied to this Discord ID until you re-link. Continue?')) return
  await unlinkDiscord()
  await refresh()
  await hydrate()
}

async function doRevokeSession(jti: string) {
  await revokeSession(jti)
  sessions.value = sessions.value.filter(s => s.jti !== jti)
}

async function doEndOthers() {
  if (!confirm('End every other session?')) return
  await endOtherSessions()
  sessions.value = sessions.value.filter(s => s.current)
}

async function doDeleteAccount() {
  deleteError.value = null
  deleteBusy.value = true
  try {
    await deleteAccount(deleteConfirm.value)
    clear()
    await router.replace('/')
  } catch (e) {
    const error = e as {response?: {data?: string}}
    deleteError.value = typeof error.response?.data === 'string' ? error.response.data : 'Account deletion failed.'
  } finally {
    deleteBusy.value = false
  }
}

function fmt(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString()
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader>
      {{ t('page.account.security.security') }}
    </PageHeader>
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else-if="summary">
      <section class="rounded-theme border border-border-light dark:border-border-dark p-4">
        <CardHeader>
          {{ t('auth.password') }}
        </CardHeader>
        <form class="space-y-3" @submit.prevent="doChangePassword">
          <LabelledField v-if="summary.account.hasPassword" :label="t('page.account.security.currentPassword')">
            <PasswordInput v-model="currentPassword" autocomplete="current-password" required/>
          </LabelledField>
          <LabelledField :label="t('page.account.security.newPassword')">
            <PasswordInput v-model="newPassword" autocomplete="new-password" minlength="8" required/>
          </LabelledField>
          <LabelledField :label="t('page.account.security.confirmNewPassword')">
            <PasswordInput v-model="confirmPassword" autocomplete="new-password" minlength="8" required/>
          </LabelledField>
          <div v-if="passwordError" class="text-sm text-error">
            {{ passwordError }}
          </div>
          <div v-if="passwordMessage" class="text-sm text-success">
            {{ passwordMessage }}
          </div>
          <PrimaryButton :disabled="passwordBusy" @click="doChangePassword">
            {{ summary.account.hasPassword ? 'Update password' : 'Set password' }}
          </PrimaryButton>
        </form>
      </section>

      <IdentitySection
          :account="summary.account"
          :format="fmt"
          @changed="refresh"
          @unlink="doUnlinkDiscord"
      />

      <section class="rounded-theme border border-border-light dark:border-border-dark p-4">
        <CardHeader class="flex items-center justify-between">
          <span>{{ t('page.account.security.sessions') }}</span>
          <LinkButton v-if="sessions.length > 1" class="text-xs font-normal normal-case" @click="doEndOthers">
            {{ t('page.account.security.endAllOtherSessions') }}
          </LinkButton>
        </CardHeader>
        <ul v-if="sessions.length" class="divide-y divide-border-light dark:divide-border-dark text-sm">
          <li v-for="s in sessions" :key="s.jti" class="flex items-center justify-between py-2">
            <div class="min-w-0">
              <div class="truncate font-medium">
                {{ s.userAgent ?? 'Unknown client' }}
                <PrimaryBadge v-if="s.current" class="ml-2">{{ t('page.account.security.thisDevice') }}</PrimaryBadge>
              </div>
              <div class="text-xs opacity-60">
                Started {{ fmt(s.issuedAt) }} · last seen {{ fmt(s.lastSeenAt) }}
              </div>
            </div>
            <SecondaryButton v-if="!s.current" compact @click="doRevokeSession(s.jti)">{{ t('page.account.security.end') }}</SecondaryButton>
          </li>
        </ul>
        <div v-else class="text-sm opacity-70">
          {{ t('page.account.security.noActiveSessionsFound') }}
        </div>
      </section>

      <section class="rounded-theme border border-error/40 p-4">
        <CardHeader tone="error">
          {{ t('page.account.security.deleteAccount') }}
        </CardHeader>
        <p class="mb-3 text-sm">
          {{ t('page.account.security.deletingYourAccountRemovesYourEmail') }}
        </p>
        <LabelledField
            :help="summary.account.email ?? 'This account has no email; type anything to confirm.'"
            class="mb-3"
            :label="t('page.account.security.typeYourEmailToConfirm')"
        >
          <TextInput v-model="deleteConfirm"/>
        </LabelledField>
        <div v-if="deleteError" class="mb-2 text-sm text-error">
          {{ deleteError }}
        </div>
        <ErrorButton :disabled="deleteBusy" compact @click="doDeleteAccount">{{ t('page.account.security.deleteMyAccount') }}</ErrorButton>
      </section>
    </template>
  </div>
</template>
