<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {confirmPasswordReset} from '~/api/account'
import PrimaryButton from '~/components/button/PrimaryButton.vue'

const {t} = useI18n()

const route = useRoute()
const router = useRouter()
const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''))

const password = ref('')
const confirm = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)
const done = ref(false)

async function submit() {
  errorMessage.value = null
  if (!token.value) {
    errorMessage.value = t('page.reset-password.missingTokenUseTheLinkFrom')
    return
  }
  if (password.value.length < 8) {
    errorMessage.value = t('auth.passwordTooShort')
    return
  }
  if (password.value !== confirm.value) {
    errorMessage.value = t('auth.passwordsDiffer')
    return
  }
  submitting.value = true
  try {
    await confirmPasswordReset(token.value, password.value)
    done.value = true
  } catch (e) {
    const err = e as {response?: {status?: number, data?: string}}
    if (err.response?.status === 410) errorMessage.value = t('page.reset-password.thisResetLinkHasExpiredRequest')
    else errorMessage.value = typeof err.response?.data === 'string' ? err.response.data : 'Reset failed.'
  } finally {
    submitting.value = false
  }
}

async function goToLogin() {
  await router.replace('/login')
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-md flex-col justify-center p-6">
    <PageHeader class="mb-6">
      {{ t('page.reset-password.setANewPassword') }}
    </PageHeader>
    <template v-if="!done">
      <form class="space-y-4" @submit.prevent="submit">
        <LabelledField :label="t('page.reset-password.newPassword')">
          <PasswordInput v-model="password" autocomplete="new-password" minlength="8" required/>
        </LabelledField>
        <LabelledField :label="t('page.reset-password.confirmNewPassword')">
          <PasswordInput v-model="confirm" autocomplete="new-password" minlength="8" required/>
        </LabelledField>
        <div v-if="errorMessage" class="rounded-theme border border-error/40 bg-error/10 p-2 text-sm text-error">
          {{ errorMessage }}
        </div>
        <PrimaryButton :disabled="submitting" full-width @click="submit">
          {{ submitting ? 'Updating…' : 'Set password' }}
        </PrimaryButton>
      </form>
    </template>
    <div v-else class="space-y-4">
      <p class="rounded-theme border border-success/40 bg-success/10 p-3 text-sm">
        {{ t('page.reset-password.passwordUpdatedYouCanLogIn') }}
      </p>
      <PrimaryButton full-width @click="goToLogin">
        {{ t('page.reset-password.goToLogin') }}
      </PrimaryButton>
    </div>
  </main>
</template>
