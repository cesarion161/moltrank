import NextAuth, { NextAuthOptions } from 'next-auth'
import TwitterProvider from 'next-auth/providers/twitter'

export const authOptions: NextAuthOptions = {
  providers: [
    TwitterProvider({
      clientId: process.env.TWITTER_CLIENT_ID!,
      clientSecret: process.env.TWITTER_CLIENT_SECRET!,
      version: '2.0', // Use Twitter OAuth 2.0
    }),
  ],
  callbacks: {
    async signIn({ user, account, profile }) {
      // The identity linking will be handled client-side
      // We just ensure the auth succeeds here
      return true
    },
    async session({ session, token }) {
      // Add Twitter account info to session
      if (token.account) {
        session.user.account = token.account
      }
      return session
    },
    async jwt({ token, account, profile }) {
      // Persist account info in the token
      if (account) {
        token.account = {
          provider: account.provider,
          providerAccountId: account.providerAccountId,
          username: (profile as any)?.data?.username || (profile as any)?.screen_name,
        }
      }
      return token
    },
  },
  pages: {
    signIn: '/auth/signin',
    error: '/auth/error',
  },
}

const handler = NextAuth(authOptions)

export { handler as GET, handler as POST }
