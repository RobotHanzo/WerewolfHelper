import random
import time
import typing

import discord
import discord_slash.model
from discord.ext import commands, tasks
from discord_slash import SlashContext, ButtonStyle, ComponentContext
from discord_slash.utils import manage_components


class Voting:
    bot: commands.Bot

    def __init__(self, bot: commands.Bot):
        self.bot = bot
        self.slash = bot.slash
        self.polls = {}
        self.routine_check.start()

    @tasks.loop(seconds=1)
    async def routine_check(self):
        to_remove = []
        for poll in self.polls.copy():
            if time.time() >= self.polls[poll]['expire']:
                await self.end_poll(poll, True)
                to_remove.append(poll)

    async def end_poll(self, poll_id: str, remove_from_list: bool):
        poll = self.polls[poll_id]
        msg = await (self.bot.get_channel(poll['channel'])).fetch_message(poll_id)
        winner = []
        for _id in poll['results']:
            if len(winner) == 0:
                winner = [{_id: poll['results'][_id]}]
                continue
            if len(list(winner[0].values())[0]) < len(poll['results'][_id]):
                winner = [{_id: poll['results'][_id]}]
                continue
            if len(list(winner[0].values())[0]) == len(poll['results'][_id]):
                winner.append({_id: poll['results'][_id]})
                continue
        parsed_winner = []
        for w in winner:
            parsed_winner.append(poll['options'][list(w.keys())[0]])
        embed = discord.Embed(title=poll['topic'], description=f'獲勝選項：{"、".join(parsed_winner)}',
                              colour=discord.Colour.from_rgb(random.randint(0, 255), random.randint(0, 255),
                                                             random.randint(0, 255)))
        for custom_id in poll['results']:
            votes = poll['results'][custom_id]
            label = poll['options'][custom_id]
            if poll['anonymous_vote']:
                embed.add_field(name=label, value=str(len(votes)) + '票')
            else:
                embed.add_field(name=label, value=f'投給此選項的：{"、".join(["<@!" + str(x) + ">" for x in votes])}')
        await msg.edit(embed=embed, components=[])
        if remove_from_list:
            self.polls.pop(poll_id)
        if poll['callback']:
            await poll['callback'](winner)
        else:
            await msg.reply(f'投票結束！獲勝選項：{"、".join(parsed_winner)}')

    async def update_embed(self, msg: discord_slash.model.SlashMessage):
        poll = self.polls[msg.id]
        embed = discord.Embed(title=poll['topic'], description=f'每人有`{poll["vote_per_user"]}`票',
                              colour=discord.Colour.from_rgb(random.randint(0, 255), random.randint(0, 255),
                                                             random.randint(0, 255)))
        buttons = []
        for custom_id in poll['results']:
            votes = poll['results'][custom_id]
            label = poll['options'][custom_id]
            buttons.append(
                manage_components.create_button(style=ButtonStyle.secondary, label=label, custom_id=custom_id))
            if poll['show_vote_count_before_ending']:
                embed.add_field(name=label, value=str(len(votes)) + '票')
            else:
                embed.add_field(name=label, value='\u200b')
        expire_after = poll['expire'] - int(time.time())
        embed.set_footer(text=f'{expire_after} 秒後計票')
        rows = []
        temp = []
        for button in buttons:
            if len(temp) == 5:
                rows.append(manage_components.create_actionrow(*temp))
                temp = [temp]
            else:
                temp.append(button)
        if len(temp) != 0:
            # put in the rest
            rows.append(manage_components.create_actionrow(*temp))
        await msg.edit(embed=embed, components=rows)

    async def generate_new_poll(self, ctx: SlashContext, topic: str, options: typing.List[typing.Tuple[str, str]],
                                vote_per_user: int, expire_after: int, show_vote_count_before_ending: bool,
                                anonymous_vote: bool, callback=None, voter_check=None):
        """
        options: A list of tuples of (label[shown to users], custom_id[not shown to users])
        callback: An async function that takes in result as its only argument and is called when the poll ends
        voter_check: An async function that takes in ctx as its only argument and is called when someone votes, must return a boolean whether the voter qualifies for voting
        """
        embed = discord.Embed(title=topic, description=f'每人有`{vote_per_user}`票',
                              colour=discord.Colour.from_rgb(random.randint(0, 255), random.randint(0, 255),
                                                             random.randint(0, 255)))
        buttons = []
        for label, custom_id in options:
            buttons.append(
                manage_components.create_button(style=ButtonStyle.secondary, label=label, custom_id=custom_id))
            if show_vote_count_before_ending:
                embed.add_field(name=label, value='0票')
            else:
                embed.add_field(name=label, value='\u200b')
        embed.set_footer(text=f'{expire_after} 秒後計票')
        rows = []
        temp = []
        for button in buttons:
            if len(temp) == 5:
                rows.append(manage_components.create_actionrow(*temp))
                temp = [button]
            else:
                temp.append(button)
        if len(temp) != 0:
            # put in the rest
            rows.append(manage_components.create_actionrow(*temp))
        msg = await ctx.send(embed=embed, components=rows)
        self.polls[msg.id] = {'expire': int(time.time()) + expire_after,
                              'show_vote_count_before_ending': show_vote_count_before_ending,
                              'vote_per_user': vote_per_user, 'topic': topic, 'options': {y: x for x, y in options},
                              'channel': msg.channel.id, 'results': {y: [] for x, y in options},
                              'anonymous_vote': anonymous_vote, 'callback': callback, 'voter_check': voter_check}

        @self.slash.component_callback(use_callback_name=False, messages=msg.id)
        async def callback(ctx: ComponentContext):
            if msg.id not in self.polls:
                await ctx.send('投票已過期，無法再投票！', hidden=True)
                return
            if voter_check:
                if not await voter_check(ctx):
                    return
            if ctx.author.id in self.polls[msg.id]['results'][ctx.custom_id]:
                self.polls[msg.id]['results'][ctx.custom_id].remove(ctx.author.id)
                await ctx.send('你取消了投給' + self.polls[msg.id]['options'][ctx.custom_id] + '的票', hidden=True)
            else:
                user_voted = []
                for x in self.polls[msg.id]['results']:
                    if ctx.author.id in self.polls[msg.id]['results'][x]:
                        user_voted.append(self.polls[msg.id]['options'][x])
                if len(user_voted) >= self.polls[msg.id]['vote_per_user']:
                    await ctx.send(f'你投太多票了！你最多只能投{self.polls[msg.id]["vote_per_user"]}票，你投給的選項：{"、".join(user_voted)}',
                                   hidden=True)
                    return
                self.polls[msg.id]['results'][ctx.custom_id].append(ctx.author.id)
                await ctx.send('你成功投給了' + self.polls[msg.id]['options'][ctx.custom_id], hidden=True)
            await self.update_embed(ctx.origin_message)
