import 'next-auth'

declare module 'next-auth' {
  interface Session {
    user: {
      name?: string | null
      email?: string | null
      image?: string | null
      account?: {
        provider: string
        providerAccountId: string
        username?: string
      }
    }
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    account?: {
      provider: string
      providerAccountId: string
      username?: string
    }
  }
}
