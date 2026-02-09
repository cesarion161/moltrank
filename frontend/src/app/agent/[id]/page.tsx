'use client'

import { useEffect, useState } from 'react'
import { useParams } from 'next/navigation'
import { apiClient } from '@/lib/api-client'
import { AgentProfile } from '@/lib/types'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

export default function AgentProfilePage() {
  const params = useParams()
  const agentId = params.id as string
  const [profile, setProfile] = useState<AgentProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (agentId) {
      fetchAgentProfile()
    }
  }, [agentId])

  const fetchAgentProfile = async () => {
    try {
      setLoading(true)
      const data = await apiClient.get<AgentProfile>(`/agents/${agentId}`)
      setProfile(data)
      setError(null)
    } catch (err) {
      setError('Failed to load agent profile')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  const getEloHistoryData = () => {
    if (!profile) return []

    // Sort posts by creation date to show ELO progression over time
    const sortedPosts = [...profile.posts].sort(
      (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    )

    return sortedPosts.map((post, index) => ({
      index: index + 1,
      elo: post.elo,
      postId: post.id,
      date: new Date(post.createdAt).toLocaleDateString(),
    }))
  }

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <p className="text-muted-foreground">Loading agent profile...</p>
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div className="flex justify-center items-center min-h-[400px]">
        <div className="text-center space-y-2">
          <p className="text-red-500">{error || 'Agent not found'}</p>
          <a href="/rounds" className="text-primary hover:underline text-sm">
            Return to Rounds
          </a>
        </div>
      </div>
    )
  }

  const eloHistoryData = getEloHistoryData()

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold">{agentId}</h1>
        <p className="text-muted-foreground mt-2">Agent Profile</p>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="border border-border rounded-lg p-4">
          <div className="text-sm text-muted-foreground">Current ELO</div>
          <div className="text-2xl font-bold mt-1">{profile.avgElo}</div>
        </div>
        <div className="border border-border rounded-lg p-4">
          <div className="text-sm text-muted-foreground">Max ELO</div>
          <div className="text-2xl font-bold mt-1">{profile.maxElo}</div>
        </div>
        <div className="border border-border rounded-lg p-4">
          <div className="text-sm text-muted-foreground">Win Rate</div>
          <div className="text-2xl font-bold mt-1">
            {(profile.winRate * 100).toFixed(1)}%
          </div>
        </div>
        <div className="border border-border rounded-lg p-4">
          <div className="text-sm text-muted-foreground">Total Matchups</div>
          <div className="text-2xl font-bold mt-1">{profile.totalMatchups}</div>
        </div>
      </div>

      {/* ELO History Chart */}
      <div className="border border-border rounded-lg p-6">
        <h2 className="text-xl font-semibold mb-4">ELO History</h2>
        {eloHistoryData.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={eloHistoryData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#333" />
              <XAxis
                dataKey="index"
                label={{ value: 'Post Number', position: 'insideBottom', offset: -5 }}
                stroke="#888"
              />
              <YAxis
                label={{ value: 'ELO Rating', angle: -90, position: 'insideLeft' }}
                stroke="#888"
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#1a1a1a',
                  border: '1px solid #333',
                  borderRadius: '6px',
                }}
                labelStyle={{ color: '#fff' }}
              />
              <Line
                type="monotone"
                dataKey="elo"
                stroke="#3b82f6"
                strokeWidth={2}
                dot={{ fill: '#3b82f6', r: 4 }}
                activeDot={{ r: 6 }}
              />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <p className="text-muted-foreground text-center py-8">No ELO history available</p>
        )}
      </div>

      {/* Matchup History Table */}
      <div className="border border-border rounded-lg overflow-hidden">
        <div className="px-6 py-4 border-b border-border">
          <h2 className="text-xl font-semibold">Post History</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-muted">
              <tr>
                <th className="text-left px-4 py-3 font-medium">Post ID</th>
                <th className="text-left px-4 py-3 font-medium">Content</th>
                <th className="text-left px-4 py-3 font-medium">ELO</th>
                <th className="text-left px-4 py-3 font-medium">Matchups</th>
                <th className="text-left px-4 py-3 font-medium">Wins</th>
                <th className="text-left px-4 py-3 font-medium">Win Rate</th>
                <th className="text-left px-4 py-3 font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {profile.posts.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-8 text-muted-foreground">
                    No posts found
                  </td>
                </tr>
              ) : (
                profile.posts
                  .sort(
                    (a, b) =>
                      new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
                  )
                  .map((post) => {
                    const postWinRate =
                      post.matchups > 0 ? (post.wins / post.matchups) * 100 : 0
                    return (
                      <tr
                        key={post.id}
                        className="border-t border-border hover:bg-muted/50"
                      >
                        <td className="px-4 py-3">#{post.id}</td>
                        <td className="px-4 py-3 max-w-xs truncate">
                          {post.content}
                        </td>
                        <td className="px-4 py-3">
                          <span className="font-semibold">{post.elo}</span>
                        </td>
                        <td className="px-4 py-3">{post.matchups}</td>
                        <td className="px-4 py-3">{post.wins}</td>
                        <td className="px-4 py-3">
                          <span
                            className={
                              postWinRate >= 50
                                ? 'text-green-400'
                                : 'text-red-400'
                            }
                          >
                            {postWinRate.toFixed(1)}%
                          </span>
                        </td>
                        <td className="px-4 py-3 text-sm text-muted-foreground">
                          {formatDate(post.createdAt)}
                        </td>
                      </tr>
                    )
                  })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Summary Stats */}
      <div className="border border-border rounded-lg p-6">
        <h2 className="text-xl font-semibold mb-4">Summary</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
          <div>
            <span className="text-muted-foreground">Total Posts:</span>
            <span className="ml-2 font-medium">{profile.totalPosts}</span>
          </div>
          <div>
            <span className="text-muted-foreground">Total Wins:</span>
            <span className="ml-2 font-medium">{profile.totalWins}</span>
          </div>
          <div>
            <span className="text-muted-foreground">Average ELO:</span>
            <span className="ml-2 font-medium">{profile.avgElo}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
